package me.infinity.groupstats.manager;

import dev.dejvokep.boostedyaml.YamlDocument;
import me.infinity.groupstats.GroupStatsPlugin;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.exceptions.JedisException;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Manages the connection to a Redis server and provides asynchronous methods
 * for Redis operations. Uses JedisPool for efficient connection management.
 */
public class RedisManager {

    private final GroupStatsPlugin plugin;
    private JedisPool jedisPool;
    private final ExecutorService asyncExecutor;
    private boolean enabled;

    public RedisManager(GroupStatsPlugin plugin, YamlDocument config) {
        this.plugin = plugin;
        // It's good practice to use a dedicated thread pool for these async DB operations
        // rather than relying solely on ForkJoinPool.commonPool() for all CompletableFutures.
        this.asyncExecutor = Executors.newFixedThreadPool(Math.max(2, config.getInt("redis.pool.max-total", 8) / 2),
                runnable -> {
                    Thread thread = new Thread(runnable);
                    thread.setName("GroupStats-Redis-Worker-" + thread.getId());
                    thread.setDaemon(true); // Allow JVM to exit if only these threads are running
                    return thread;
                });
        loadConfigAndInit(config);
    }

    private void loadConfigAndInit(YamlDocument config) {
        this.enabled = config.getBoolean("redis.enabled", false);
        if (!enabled) {
            plugin.getLogger().info("[RedisManager] Redis is disabled in the configuration.");
            return;
        }

        String host = config.getString("redis.host", "127.0.0.1");
        int port = config.getInt("redis.port", 6379);
        String password = config.getString("redis.password", "");
        int timeout = config.getInt("redis.timeout", 2000);
        int database = config.getInt("redis.database", 0);

        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(config.getInt("redis.pool.max-total", 8));
        poolConfig.setMaxIdle(config.getInt("redis.pool.max-idle", 8));
        poolConfig.setMinIdle(config.getInt("redis.pool.min-idle", 0));
        // It's good to test on borrow to ensure connections are healthy
        poolConfig.setTestOnBorrow(true);
        poolConfig.setTestOnReturn(true);
        poolConfig.setTestWhileIdle(true);


        try {
            if (password == null || password.isEmpty()) {
                this.jedisPool = new JedisPool(poolConfig, host, port, timeout, null, database);
            } else {
                this.jedisPool = new JedisPool(poolConfig, host, port, timeout, password, database);
            }

            // Test connection
            try (Jedis jedis = jedisPool.getResource()) {
                String pingResponse = jedis.ping();
                if ("PONG".equalsIgnoreCase(pingResponse)) {
                    plugin.getLogger().info("[RedisManager] Successfully connected to Redis server at " + host + ":" + port);
                } else {
                    plugin.getLogger().warning("[RedisManager] Connected to Redis, but PING response was unexpected: " + pingResponse);
                }
            }
        } catch (JedisException e) {
            plugin.getLogger().severe("[RedisManager] Failed to connect to Redis server at " + host + ":" + port + ". Error: " + e.getMessage());
            plugin.getLogger().severe("[RedisManager] Stack trace: " + e);
            this.jedisPool = null; // Ensure pool is null if connection failed
            this.enabled = false; // Disable Redis usage if connection fails
        }
    }

    public boolean isEnabled() {
        return enabled && this.jedisPool != null;
    }

    private <T> CompletableFuture<T> supplyAsync(RedisCommand<T> command) {
        if (!isEnabled()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Redis is not enabled or connected."));
        }
        return CompletableFuture.supplyAsync(() -> {
            try (Jedis jedis = jedisPool.getResource()) {
                return command.execute(jedis);
            } catch (JedisException e) {
                plugin.getLogger().warning("[RedisManager] JedisException during operation: " + e.getMessage());
                throw e; // Rethrow to fail the CompletableFuture
            }
        }, asyncExecutor);
    }

    private CompletableFuture<Void> runAsync(RedisAction action) {
        if (!isEnabled()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Redis is not enabled or connected."));
        }
        return CompletableFuture.runAsync(() -> {
            try (Jedis jedis = jedisPool.getResource()) {
                action.execute(jedis);
            } catch (JedisException e) {
                plugin.getLogger().warning("[RedisManager] JedisException during operation: " + e.getMessage());
                throw e; // Rethrow to fail the CompletableFuture
            }
        }, asyncExecutor);
    }

    // --- Jedis Commands ---

    public CompletableFuture<String> get(String key) {
        return supplyAsync(jedis -> jedis.get(key));
    }

    public CompletableFuture<String> set(String key, String value) {
        return supplyAsync(jedis -> jedis.set(key, value));
    }

    public CompletableFuture<String> setex(String key, long seconds, String value) {
        return supplyAsync(jedis -> jedis.setex(key, seconds, value));
    }

    public CompletableFuture<Long> del(String... keys) {
        return supplyAsync(jedis -> jedis.del(keys));
    }

    public CompletableFuture<Boolean> exists(String key) {
        return supplyAsync(jedis -> jedis.exists(key));
    }

    public CompletableFuture<Long> incr(String key) {
        return supplyAsync(jedis -> jedis.incr(key));
    }

    public CompletableFuture<Long> incrBy(String key, long increment) {
        return supplyAsync(jedis -> jedis.incrBy(key, increment));
    }

    public CompletableFuture<Long> decr(String key) {
        return supplyAsync(jedis -> jedis.decr(key));
    }

    public CompletableFuture<Long> decrBy(String key, long decrement) {
        return supplyAsync(jedis -> jedis.decrBy(key, decrement));
    }

    // Hash operations
    public CompletableFuture<String> hget(String key, String field) {
        return supplyAsync(jedis -> jedis.hget(key, field));
    }

    public CompletableFuture<Long> hset(String key, String field, String value) {
        return supplyAsync(jedis -> jedis.hset(key, field, value));
    }

    public CompletableFuture<String> hmset(String key, Map<String, String> hash) {
        return supplyAsync(jedis -> jedis.hmset(key, hash));
    }

    public CompletableFuture<Map<String, String>> hgetAll(String key) {
        return supplyAsync(jedis -> jedis.hgetAll(key));
    }

    public CompletableFuture<Long> hdel(String key, String... fields) {
        return supplyAsync(jedis -> jedis.hdel(key, fields));
    }

    public CompletableFuture<Boolean> hexists(String key, String field) {
        return supplyAsync(jedis -> jedis.hexists(key, field));
    }

    public CompletableFuture<Long> hincrBy(String key, String field, long value) {
        return supplyAsync(jedis -> jedis.hincrBy(key, field, value));
    }

    // Set operations
    public CompletableFuture<Long> sadd(String key, String... members) {
        return supplyAsync(jedis -> jedis.sadd(key, members));
    }

    public CompletableFuture<Long> srem(String key, String... members) {
        return supplyAsync(jedis -> jedis.srem(key, members));
    }

    public CompletableFuture<Set<String>> smembers(String key) {
        return supplyAsync(jedis -> jedis.smembers(key));
    }

    public CompletableFuture<Boolean> sismember(String key, String member) {
        return supplyAsync(jedis -> jedis.sismember(key, member));
    }

    // List operations
    public CompletableFuture<Long> lpush(String key, String... strings) {
        return supplyAsync(jedis -> jedis.lpush(key, strings));
    }

    public CompletableFuture<Long> rpush(String key, String... strings) {
        return supplyAsync(jedis -> jedis.rpush(key, strings));
    }

    public CompletableFuture<String> lpop(String key) {
        return supplyAsync(jedis -> jedis.lpop(key));
    }

    public CompletableFuture<String> rpop(String key) {
        return supplyAsync(jedis -> jedis.rpop(key));
    }

    public CompletableFuture<List<String>> lrange(String key, long start, long end) {
        return supplyAsync(jedis -> jedis.lrange(key, start, end));
    }

    public CompletableFuture<Long> llen(String key) {
        return supplyAsync(jedis -> jedis.llen(key));
    }


    public void shutdown() {
        plugin.getLogger().info("[RedisManager] Shutting down Redis connection pool...");
        if (jedisPool != null && !jedisPool.isClosed()) {
            jedisPool.close(); // Use close() for JedisPool >= 3.0, destroy() for older
        }
        asyncExecutor.shutdown();
        try {
            if (!asyncExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                asyncExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            asyncExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        plugin.getLogger().info("[RedisManager] Redis connection pool shut down.");
    }

    @FunctionalInterface
    private interface RedisCommand<T> {
        T execute(Jedis jedis);
    }

    @FunctionalInterface
    private interface RedisAction {
        void execute(Jedis jedis);
    }

    /**
     * Synchronously gets the value of a hash field.
     * !! WARNING: This is a synchronous Redis call and should ONLY be used in contexts
     * where a very fast, non-blocking call is acceptable, such as certain PlaceholderAPI scenarios
     * where async is not supported and data is expected to be readily available.
     * Avoid using this on the main server thread if it might interact with a slow Redis instance
     * or involve large data. Prefer asynchronous methods for all other use cases.
     *
     * @param key   The key of the hash.
     * @param field The field in the hash.
     * @return The value of the field, or null if the field or key does not exist.
     */
    public String hgetDirect(String key, String field) {
        if (!isEnabled()) {
            plugin.getLogger().finer("[RedisManager] hgetDirect called while Redis is disabled for key: " + key);
            return null; // Or throw exception, but for PAPI, null/default is often better
        }
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.hget(key, field);
        } catch (JedisException e) {
            plugin.getLogger().warning("[RedisManager] JedisException during synchronous hgetDirect for key " + key + ", field " + field + ": " + e.getMessage());
            return null; // Or handle error appropriately for PAPI (e.g., return "Error")
        }
    }
}
