package me.infinity.groupstats.manager;

import com.google.gson.Gson;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import me.infinity.groupstats.models.GroupProfile;
import me.infinity.groupstats.GroupNode;
import java.util.UUID;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;
import java.util.LinkedHashSet;

public class RedisCacheManager {
    private final JedisPool jedisPool;
    private final Gson gson;
    private final int cacheTTL;
    
    private static final String PROFILE_KEY_PREFIX = "groupstats:profile:";
    private static final String LEADERBOARD_PREFIX = "groupstats:leaderboard:";
    private static final String ACTIVE_PLAYERS_KEY = "groupstats:active_players";
    
    public RedisCacheManager(JedisPool jedisPool, Gson gson, int cacheTTL) {
        this.jedisPool = jedisPool;
        this.gson = gson;
        this.cacheTTL = cacheTTL;
    }
    
    public void cacheProfile(UUID playerId, GroupProfile profile) {
        if (jedisPool == null) return;
        
        try (Jedis jedis = jedisPool.getResource()) {
            String key = PROFILE_KEY_PREFIX + playerId.toString();
            String json = gson.toJson(profile);
            jedis.setex(key, cacheTTL, json);
            
            jedis.sadd(ACTIVE_PLAYERS_KEY, playerId.toString());
        } catch (Exception e) {
            
        }
    }
    
    public GroupProfile getCachedProfile(UUID playerId) {
        if (jedisPool == null) return null;
        
        try (Jedis jedis = jedisPool.getResource()) {
            String key = PROFILE_KEY_PREFIX + playerId.toString();
            String json = jedis.get(key);
            if (json != null) {
                return gson.fromJson(json, GroupProfile.class);
            }
        } catch (Exception e) {
            
        }
        return null;
    }
    
    public void updateLeaderboards(UUID playerId, String gameMode, GroupNode stats) {
        if (jedisPool == null) return;
        
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.zadd(LEADERBOARD_PREFIX + gameMode + ":wins", stats.getWins().get(), playerId.toString());
            jedis.zadd(LEADERBOARD_PREFIX + gameMode + ":kills", stats.getKills().get(), playerId.toString());
            jedis.zadd(LEADERBOARD_PREFIX + gameMode + ":winstreak", stats.getWinstreak().get(), playerId.toString());
            
            double kdr = stats.getDeaths().get() > 0 ? (double) stats.getKills().get() / stats.getDeaths().get() : stats.getKills().get();
            jedis.zadd(LEADERBOARD_PREFIX + gameMode + ":kdr", kdr, playerId.toString());
            
            double fkdr = stats.getFinalDeaths().get() > 0 ? (double) stats.getFinalKills().get() / stats.getFinalDeaths().get() : stats.getFinalKills().get();
            jedis.zadd(LEADERBOARD_PREFIX + gameMode + ":fkdr", fkdr, playerId.toString());
            
            double wlr = stats.getLosses().get() > 0 ? (double) stats.getWins().get() / stats.getLosses().get() : stats.getWins().get();
            jedis.zadd(LEADERBOARD_PREFIX + gameMode + ":wlr", wlr, playerId.toString());
        } catch (Exception e) {
            
        }
    }
    
    public Set<String> getLeaderboard(String gameMode, String statType, int limit) {
        if (jedisPool == null) return Collections.emptySet();
        
        try (Jedis jedis = jedisPool.getResource()) {
            List<String> result = jedis.zrevrange(LEADERBOARD_PREFIX + gameMode + ":" + statType, 0, limit - 1);
            return new LinkedHashSet<>(result);
        } catch (Exception e) {
            return Collections.emptySet();
        }
    }
    
    public void removePlayer(UUID playerId) {
        if (jedisPool == null) return;
        
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.del(PROFILE_KEY_PREFIX + playerId.toString());
            jedis.srem(ACTIVE_PLAYERS_KEY, playerId.toString());
        } catch (Exception e) {
            
        }
    }
    
    public long getActivePlayerCount() {
        if (jedisPool == null) return 0;
        
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.scard(ACTIVE_PLAYERS_KEY);
        } catch (Exception e) {
            return 0;
        }
    }
    
    public void publishStatUpdate(String channel, String message) {
        if (jedisPool == null) return;
        
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.publish(channel, message);
        } catch (Exception e) {
            
        }
    }
}