package me.infinity.groupstats.listeners;

import lombok.RequiredArgsConstructor;
import me.infinity.groupstats.manager.DatabaseController;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.UUID;

@RequiredArgsConstructor
public class ProfileJoinListener implements Listener {

    private final DatabaseController databaseController;

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        UUID uniqueId = event.getPlayer().getUniqueId();
        databaseController.upsert(uniqueId);
    }
}