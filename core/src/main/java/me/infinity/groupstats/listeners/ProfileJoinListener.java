package me.infinity.groupstats.listeners;

import lombok.RequiredArgsConstructor;
import me.infinity.groupstats.GroupStatsPlugin;
import me.infinity.groupstats.models.GroupProfile;
import me.infinity.groupstats.manager.GroupManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RequiredArgsConstructor
public class ProfileJoinListener implements Listener {

    private final GroupManager groupManager;
    private final GroupStatsPlugin instance;

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        UUID uniqueId = event.getPlayer().getUniqueId();

        this.instance.getServer().getScheduler().runTaskLater(this.instance, () -> {
            GroupProfile profile = this.groupManager.load(uniqueId);
            profile.setUniqueId(uniqueId);
            this.groupManager.getCache().put(uniqueId, profile);
        }, 20L);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        CompletableFuture.runAsync(() -> {
            UUID uniqueId = event.getPlayer().getUniqueId();

            GroupProfile profile = this.groupManager.getCache().get(uniqueId);
            this.groupManager.save(profile);
            this.groupManager.getCache().remove(uniqueId);
        });
    }
}