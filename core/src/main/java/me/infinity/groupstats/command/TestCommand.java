    package me.infinity.groupstats.command;

    import lombok.RequiredArgsConstructor;
    import me.infinity.groupstats.models.GroupProfile;
    import me.infinity.groupstats.GroupStatsPlugin;
    import org.bukkit.command.Command;
    import org.bukkit.command.CommandExecutor;
    import org.bukkit.command.CommandSender;
    import org.bukkit.entity.Player;

    @RequiredArgsConstructor
    public class TestCommand implements CommandExecutor {

        private final GroupStatsPlugin instance;

        @Override
        public boolean onCommand(CommandSender commandSender, Command command, String s, String[] strings) {

            if (!(commandSender instanceof Player)) return true;

            Player player = (Player) commandSender;
            GroupProfile profile = instance.getGroupManager().getCache().get(player.getUniqueId());
            player.sendMessage(profile.toString());

            return true;
        }
    }
