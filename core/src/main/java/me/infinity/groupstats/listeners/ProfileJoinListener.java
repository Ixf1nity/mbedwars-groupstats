package me.infinity.groupstats.listeners;

import lombok.RequiredArgsConstructor;
import me.infinity.groupstats.models.GroupProfile;
import me.infinity.groupstats.manager.GroupManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

@RequiredArgsConstructor
public class ProfileJoinListener implements Listener {

    private final GroupManager groupManager;

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        UUID uniqueId = event.getPlayer().getUniqueId();
        GroupProfile profile = this.groupManager.load(uniqueId);
        profile.setUniqueId(uniqueId);
        this.groupManager.getCache().put(uniqueId, profile);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        UUID uniqueId = event.getPlayer().getUniqueId();

        GroupProfile profile = this.groupManager.getCache().get(uniqueId);
        this.groupManager.save(profile);
        this.groupManager.removeFromCache(uniqueId);
    }
}

