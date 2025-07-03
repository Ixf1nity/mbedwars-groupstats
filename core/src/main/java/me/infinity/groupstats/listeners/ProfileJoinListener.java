package me.infinity.groupstats.listeners;

import lombok.RequiredArgsConstructor;
import me.infinity.groupstats.GroupStatsPlugin;
import me.infinity.groupstats.manager.PlayerStatsManager;
import me.infinity.groupstats.manager.ProxySyncManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

@RequiredArgsConstructor
public class ProfileJoinListener implements Listener {

    private final PlayerStatsManager playerStatsManager;
    private final ProxySyncManager proxySyncManager;
    private final GroupStatsPlugin plugin; // Keep plugin for logging or pass logger

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();

        if (proxySyncManager.isLobbyServer()) {
            // Lobby servers might have different logic, e.g., loading a global profile
            // For now, we assume they don't load group-specific stats into Redis this way.
            plugin.getLogger().fine("[ProfileJoinListener] Player " + player.getName() + " joined a lobby server. Skipping group stats load.");
            return;
        }

        String groupKey = proxySyncManager.getGroupServerKey();
        if (groupKey == null || groupKey.isEmpty() || "UNKNOWN_GROUP".equals(groupKey)) {
            plugin.getLogger().warning("[ProfileJoinListener] Server group key is not properly configured. Cannot load stats for player " + player.getName());
            return;
        }

        plugin.getLogger().fine("[ProfileJoinListener] Player " + player.getName() + " joined. Loading/warming stats for group: " + groupKey);
        playerStatsManager.getPlayerGroupStats(playerUUID, groupKey)
            .thenAccept(stats -> {
                if (stats != null) {
                    plugin.getLogger().fine("[ProfileJoinListener] Stats loaded/warmed for " + player.getName() + " in group " + groupKey);
                    // Optionally, could update player's display name, scoreboard, etc., here if needed based on loaded stats
                } else {
                     plugin.getLogger().warning("[ProfileJoinListener] Failed to load stats for " + player.getName() + " in group " + groupKey);
                }
            })
            .exceptionally(ex -> {
                plugin.getLogger().severe("[ProfileJoinListener] Exception while loading stats for player " + player.getName() + " (UUID: " + playerUUID + ") in group " + groupKey + ": " + ex.getMessage());
                ex.printStackTrace();
                return null;
            });
    }

    @EventHandler(priority = EventPriority.MONITOR) // MONITOR to ensure it's one of the last things to run for quit
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();

        if (proxySyncManager.isLobbyServer()) {
            // Lobby servers typically don't manage persistence of group-specific stats this way.
            plugin.getLogger().fine("[ProfileJoinListener] Player " + player.getName() + " quit a lobby server. Skipping group stats persistence.");
            return;
        }

        // PlayerStatsManager's handlePlayerQuit already uses ProxySyncManager to get the groupKey
        // and handles persistence from Redis to MongoDB.
        plugin.getLogger().fine("[ProfileJoinListener] Player " + player.getName() + " quit. Triggering stat persistence for server's group.");
        playerStatsManager.handlePlayerQuit(playerUUID)
            .exceptionally(ex -> {
                plugin.getLogger().severe("[ProfileJoinListener] Exception while persisting stats for player " + player.getName() + " (UUID: " + playerUUID + "): " + ex.getMessage());
                ex.printStackTrace();
                return null;
            });
    }
}