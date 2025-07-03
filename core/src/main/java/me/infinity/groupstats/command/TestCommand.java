package me.infinity.groupstats.command;

import lombok.RequiredArgsConstructor;
import me.infinity.groupstats.GroupStatsPlugin;
import me.infinity.groupstats.manager.PlayerStatsManager;
import me.infinity.groupstats.manager.ProxySyncManager;
import me.infinity.groupstats.models.PlayerGroupStats;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;

@RequiredArgsConstructor
public class TestCommand implements CommandExecutor {

    private final GroupStatsPlugin plugin;
    private final PlayerStatsManager playerStatsManager;
    private final ProxySyncManager proxySyncManager;

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (proxySyncManager.isLobbyServer()) {
            sender.sendMessage(ChatColor.RED + "This command provides group-specific stats and is not intended for lobby servers.");
            // Or, we could fetch some global summary if that existed. For now, restrict.
            return true;
        }

        String groupKey = proxySyncManager.getGroupServerKey();
        if (groupKey == null || groupKey.isEmpty() || "UNKNOWN_GROUP".equals(groupKey)) {
            sender.sendMessage(ChatColor.RED + "Server group key is not configured. Cannot display stats.");
            return true;
        }

        UUID targetUUID;
        String targetName;

        if (args.length > 0) {
            targetName = args[0];
            // Bukkit.getPlayer is only for online players. For stats, we might want offline players too.
            OfflinePlayer offlineTarget = Bukkit.getOfflinePlayer(targetName);
            if (offlineTarget == null || !offlineTarget.hasPlayedBefore() && !offlineTarget.isOnline()) { // Check if player exists
                sender.sendMessage(ChatColor.RED + "Player " + targetName + " not found.");
                return true;
            }
            targetUUID = offlineTarget.getUniqueId();
        } else {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "Please specify a player name when running from console.");
                return true;
            }
            Player player = (Player) sender;
            targetUUID = player.getUniqueId();
            targetName = player.getName();
        }

        sender.sendMessage(ChatColor.YELLOW + "Fetching " + groupKey + " stats for " + targetName + "...");

        playerStatsManager.getPlayerGroupStats(targetUUID, groupKey)
            .thenAcceptAsync(stats -> {
                if (stats == null || stats.getStats() == null || stats.getStats().isEmpty()) {
                    sender.sendMessage(ChatColor.GOLD + targetName + "'s " + ChatColor.AQUA + groupKey + ChatColor.GOLD + " Stats: " + ChatColor.WHITE + "No stats found or player has not played this group.");
                    return;
                }
                sender.sendMessage(ChatColor.GOLD + "--- " + targetName + "'s " + ChatColor.AQUA + groupKey + ChatColor.GOLD + " Stats ---");
                for (Map.Entry<String, Object> entry : stats.getStats().entrySet()) {
                    sender.sendMessage(ChatColor.GRAY + entry.getKey() + ": " + ChatColor.WHITE + entry.getValue().toString());
                }
            }, runnable -> Bukkit.getScheduler().runTask(plugin, runnable)) // Ensure response is on main thread for Bukkit API (sendMessage)
            .exceptionally(ex -> {
                sender.sendMessage(ChatColor.RED + "An error occurred while fetching stats: " + ex.getMessage());
                plugin.getLogger().severe("Error in /gstest command for " + targetName + ": " + ex.getMessage());
                ex.printStackTrace();
                return null;
            });

        return true;
    }
}