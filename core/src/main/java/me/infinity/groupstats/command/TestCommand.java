package me.infinity.groupstats.command;

import lombok.RequiredArgsConstructor;
import me.infinity.groupstats.models.GroupProfile;
import me.infinity.groupstats.GroupStatsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@RequiredArgsConstructor
public class TestCommand implements CommandExecutor {

    private final GroupStatsPlugin instance;

    @Override
    public boolean onCommand(CommandSender commandSender, Command command, String s, String[] strings) {

        if (!(commandSender instanceof Player)) {
            commandSender.sendMessage("Only players can use this command!");
            return true;
        }

        Player player = (Player) commandSender;

        // Check for too many arguments
        if (strings.length > 1) {
            player.sendMessage("§cUsage: /gstest [player]");
            return true;
        }

        GroupProfile profile;

        if (strings.length == 1) {
            // Check for target player
            String targetName = strings[0];
            Player target = Bukkit.getPlayer(targetName);

            if (target == null) {
                player.sendMessage("§cPlayer " + targetName + " not found or offline!");
                return true;
            }

            profile = instance.getGroupManager().getCache().get(target.getUniqueId());
            player.sendMessage("§a" + target.getName() + "'s stats: §f" + profile.toString());
        } else {
            // Show own stats
            profile = instance.getGroupManager().getCache().get(player.getUniqueId());
            player.sendMessage("§aYour stats: §f" + profile.toString());
        }

        return true;
    }
}