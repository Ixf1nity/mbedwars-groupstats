package me.infinity.groupstats.command;

import lombok.RequiredArgsConstructor;
import me.infinity.groupstats.GroupStatsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@RequiredArgsConstructor
public class StatusCommand implements CommandExecutor {

    private final GroupStatsPlugin plugin;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");
    private static final String SERVER_HEARTBEAT_KEY = "groupstats:servers:heartbeat";
    private static final String SERVER_INFO_KEY = "groupstats:servers:info:";
    private static final int HEARTBEAT_TIMEOUT = 30;

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        
        if (!sender.hasPermission("groupstats.admin")) {
            sender.sendMessage("§cYou don't have permission to use this command!");
            return true;
        }

        sender.sendMessage("§8§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        sender.sendMessage("§6§lGroupStats Network Status");
        sender.sendMessage("§8§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        
        sender.sendMessage("§e§lCurrent Server:");
        String serverName = getServerName();
        sender.sendMessage("§7• Name: §f" + serverName);
        sender.sendMessage("§7• Version: §f" + Bukkit.getVersion());
        sender.sendMessage("§7• Online Players: §f" + Bukkit.getOnlinePlayers().size() + "/" + Bukkit.getMaxPlayers());
        sender.sendMessage("§7• Cache Size: §f" + plugin.getGroupManager().getCache().size() + " profiles");
        sender.sendMessage("");
        
        updateServerHeartbeat(serverName);
        
        sender.sendMessage("§e§lRedis Status:");
        checkRedisStatus(sender);
        sender.sendMessage("");
        
        sender.sendMessage("§e§lConnected Servers Network:");
        checkConnectedServers(sender);
        sender.sendMessage("");
        
        sender.sendMessage("§e§lMongoDB Status:");
        checkMongoStatus(sender);
        
        sender.sendMessage("§8§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        
        return true;
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
    
    private void updateServerHeartbeat(String serverName) {
        if (!plugin.getRedisConnector().isEnabled()) {
            return;
        }
        
        CompletableFuture.runAsync(() -> {
            try (Jedis jedis = plugin.getRedisConnector().getJedisPool().getResource()) {
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
                jedis.expire(SERVER_INFO_KEY + serverName, HEARTBEAT_TIMEOUT * 2);
                
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to update server heartbeat: " + e.getMessage());
            }
        });
    }
    
    private void checkRedisStatus(CommandSender sender) {
        if (!plugin.getRedisConnector().isEnabled()) {
            sender.sendMessage("§7• Status: §cDisabled");
            sender.sendMessage("§7• Reason: §fRedis is disabled in config");
            return;
        }
        
        JedisPool pool = plugin.getRedisConnector().getJedisPool();
        if (pool == null || pool.isClosed()) {
            sender.sendMessage("§7• Status: §cDisconnected");
            sender.sendMessage("§7• Reason: §fConnection pool is closed");
            return;
        }
        
        try (Jedis jedis = pool.getResource()) {
            String pong = jedis.ping();
            sender.sendMessage("§7• Status: §aConnected (" + pong + ")");
            sender.sendMessage("§7• Host: §f" + plugin.getConfiguration().getString("REDIS.HOST"));
            sender.sendMessage("§7• Port: §f" + plugin.getConfiguration().getInt("REDIS.PORT"));
            sender.sendMessage("§7• Database: §f" + plugin.getConfiguration().getInt("REDIS.DATABASE"));
            sender.sendMessage("§7• Pool Active: §f" + pool.getNumActive());
            sender.sendMessage("§7• Pool Idle: §f" + pool.getNumIdle());
            
        } catch (Exception e) {
            sender.sendMessage("§7• Status: §cError");
            sender.sendMessage("§7• Error: §f" + e.getMessage());
        }
    }
    
    private void checkConnectedServers(CommandSender sender) {
        if (!plugin.getRedisConnector().isEnabled()) {
            sender.sendMessage("§7• Network: §cUnavailable (Redis disabled)");
            return;
        }
        
        CompletableFuture.runAsync(() -> {
            try (Jedis jedis = plugin.getRedisConnector().getJedisPool().getResource()) {
                Map<String, String> heartbeats = jedis.hgetAll(SERVER_HEARTBEAT_KEY);
                long currentTime = System.currentTimeMillis();
                
                List<String> onlineServers = new ArrayList<>();
                List<String> offlineServers = new ArrayList<>();
                
                for (Map.Entry<String, String> entry : heartbeats.entrySet()) {
                    String serverName = entry.getKey();
                    long lastHeartbeat = Long.parseLong(entry.getValue());
                    long timeDiff = (currentTime - lastHeartbeat) / 1000;
                    
                    if (timeDiff <= HEARTBEAT_TIMEOUT) {
                        onlineServers.add(serverName);
                    } else {
                        offlineServers.add(serverName);
                        jedis.hdel(SERVER_HEARTBEAT_KEY, serverName);
                        jedis.del(SERVER_INFO_KEY + serverName);
                    }
                }
                
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (onlineServers.isEmpty() && offlineServers.isEmpty()) {
                        sender.sendMessage("§7• Network: §eNo servers detected");
                        sender.sendMessage("§7• Note: §fServers need to run /gsstatus to register");
                    } else {
                        sender.sendMessage("§7• Total Servers: §f" + (onlineServers.size() + offlineServers.size()));
                        sender.sendMessage("§7• Online: §a" + onlineServers.size() + " §7| Offline: §c" + offlineServers.size());
                        sender.sendMessage("");
                        
                        if (!onlineServers.isEmpty()) {
                            sender.sendMessage("§a§lOnline Servers:");
                            for (String serverName : onlineServers) {
                                showServerDetails(sender, serverName, jedis);
                            }
                        }
                        
                        if (!offlineServers.isEmpty()) {
                            sender.sendMessage("§c§lRecently Offline:");
                            for (String serverName : offlineServers) {
                                sender.sendMessage("§7  • §c" + serverName + " §7(Timed out)");
                            }
                        }
                    }
                });
                
            } catch (Exception e) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    sender.sendMessage("§7• Network: §cError checking servers");
                    sender.sendMessage("§7• Error: §f" + e.getMessage());
                });
            }
        });
    }
    
    private void showServerDetails(CommandSender sender, String serverName, Jedis jedis) {
        try {
            Map<String, String> serverInfo = jedis.hgetAll(SERVER_INFO_KEY + serverName);
            if (!serverInfo.isEmpty()) {
                String players = serverInfo.getOrDefault("players", "?");
                String maxPlayers = serverInfo.getOrDefault("maxPlayers", "?");
                String cacheSize = serverInfo.getOrDefault("cacheSize", "?");
                String lastUpdate = serverInfo.getOrDefault("lastUpdate", "?");
                
                sender.sendMessage("§7  • §a" + serverName + " §7(" + players + "/" + maxPlayers + " players, " + cacheSize + " cached)");
                sender.sendMessage("§7    Last seen: " + lastUpdate);
            } else {
                sender.sendMessage("§7  • §a" + serverName + " §7(Details unavailable)");
            }
        } catch (Exception e) {
            sender.sendMessage("§7  • §a" + serverName + " §7(Error loading details)");
        }
    }
    
    private void checkMongoStatus(CommandSender sender) {
        if (plugin.getMongoConnector().getClient() == null) {
            sender.sendMessage("§7• Status: §cDisconnected");
            sender.sendMessage("§7• Reason: §fClient is null");
            return;
        }
        
        CompletableFuture.runAsync(() -> {
            try {
                plugin.getMongoConnector().getClient().listDatabaseNames().first();
                long profileCount = plugin.getMongoConnector().getProfiles().countDocuments();
                
                Bukkit.getScheduler().runTask(plugin, () -> {
                    sender.sendMessage("§7• Status: §aConnected");
                    sender.sendMessage("§7• Database: §f" + plugin.getMongoConnector().getDatabase().getName());
                    sender.sendMessage("§7• Collection: §fgroupstats-profiles");
                    sender.sendMessage("§7• Documents: §f" + profileCount + " profiles");
                });
                
            } catch (Exception e) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    sender.sendMessage("§7• Status: §cError");
                    sender.sendMessage("§7• Error: §f" + e.getMessage());
                });
            }
        });
    }
}