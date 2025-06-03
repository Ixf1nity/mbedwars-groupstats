package me.infinity.groupstats.manager;

import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.Jedis;
import dev.dejvokep.boostedyaml.YamlDocument;
import lombok.Getter;
import me.infinity.groupstats.GroupStatsPlugin;

@Getter
public class RedisConnector {
    private final GroupStatsPlugin plugin;
    private final YamlDocument config;
    private JedisPool jedisPool;
    
    public RedisConnector(GroupStatsPlugin plugin, YamlDocument config) {
        this.plugin = plugin;
        this.config = config;
    }
    
    public void init() {
        if (!config.getBoolean("REDIS.ENABLED", false)) {
            plugin.getLogger().info("Redis is disabled in config");
            return;
        }
        
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(config.getInt("REDIS.POOL.MAX_TOTAL", 20));
        poolConfig.setMaxIdle(config.getInt("REDIS.POOL.MAX_IDLE", 10));
        poolConfig.setMinIdle(config.getInt("REDIS.POOL.MIN_IDLE", 2));
        
        String host = config.getString("REDIS.HOST", "127.0.0.1");
        int port = config.getInt("REDIS.PORT", 6379);
        String password = config.getString("REDIS.PASSWORD", "");
        int database = config.getInt("REDIS.DATABASE", 0);
        
        if (password.isEmpty()) {
            jedisPool = new JedisPool(poolConfig, host, port, 2000, null, database);
        } else {
            jedisPool = new JedisPool(poolConfig, host, port, 2000, password, database);
        }
        
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.ping();
            plugin.getLogger().info("Redis connection established successfully!");
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to connect to Redis: " + e.getMessage());
            jedisPool.close();
            jedisPool = null;
        }
    }
    
    public void shutdown() {
        if (jedisPool != null && !jedisPool.isClosed()) {
            jedisPool.close();
            plugin.getLogger().info("Redis connection closed");
        }
    }
    
    public boolean isEnabled() {
        return jedisPool != null && !jedisPool.isClosed();
    }
}