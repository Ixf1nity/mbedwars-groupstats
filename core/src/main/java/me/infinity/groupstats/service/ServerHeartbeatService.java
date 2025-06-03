package me.infinity.groupstats.service;

import lombok.RequiredArgsConstructor;
import me.infinity.groupstats.GroupStatsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;
import redis.clients.jedis.Jedis;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@RequiredArgsConstructor
public class ServerHeartbeatService {
    
    private final GroupStatsPlugin plugin;
    private BukkitTask heartbeatTask;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");
    private static final String SERVER_HEARTBEAT_KEY = "groupstats:servers:heartbeat";
    private static final String SERVER_INFO_KEY = "groupstats:servers:info:";
    
    public void start() {
        if (!plugin.getRedisConnector().isEnabled()) {
            return;
        }
        
        heartbeatTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            sendHeartbeat();
        }, 0L, 300L);
        
        plugin.getLogger().info("Server heartbeat service started");
    }
    
    public void stop() {
        if (heartbeatTask != null) {
            heartbeatTask.cancel();
            heartbeatTask = null;
        }
        
        removeHeartbeat();
        plugin.getLogger().info("Server heartbeat service stopped");
    }
    
    private void sendHeartbeat() {
        try (Jedis jedis = plugin.getRedisConnector().getJedisPool().getResource()) {
            String serverName = getServerName();
            long timestamp = System.currentTimeMillis();
            
            jedis.hset(SERVER_HEARTBEAT_KEY, serverName, String.valueOf(timestamp));
            
            Map<String, String> serverInfo = new HashMap<>();
            serverInfo.put("name", serverName);
            serverInfo.put("version", Bukkit.getVersion());
            serverInfo.put("players", String.valueOf(Bukkit.getOnlinePlayers().size()));
            serverInfo.put("maxPlayers", String.valueOf(Bukkit.getMaxPlayers()));
            serverInfo.put("cacheSize", String.valueOf(plugin.getGroupManager().getCache().size()));
            serverInfo.put("lastUpdate", dateFormat.format(new Date(timestamp)));
            
            jedis.hmset(SERVER_INFO_KEY + serverName, serverInfo);
            jedis.expire(SERVER_INFO_KEY + serverName, 60);
            
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to send heartbeat: " + e.getMessage());
        }
    }
    
    private void removeHeartbeat() {
        if (!plugin.getRedisConnector().isEnabled()) {
            return;
        }
        
        try (Jedis jedis = plugin.getRedisConnector().getJedisPool().getResource()) {
            String serverName = getServerName();
            jedis.hdel(SERVER_HEARTBEAT_KEY, serverName);
            jedis.del(SERVER_INFO_KEY + serverName);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to remove heartbeat: " + e.getMessage());
        }
    }
    
    private String getServerName() {
        String configName = plugin.getConfiguration().getString("SERVER.NAME", "");
        if (!configName.isEmpty()) {
            return configName;
        }
        
        String bukkitName = Bukkit.getServerName();
        if (!bukkitName.equals("Unknown Server")) {
            return bukkitName;
        }
        
        return "Server-" + Bukkit.getPort();
    }
}