package me.infinity.groupstats.listeners;

package me.infinity.groupstats.listeners;

import de.marcely.bedwars.api.GameAPI;
import de.marcely.bedwars.api.arena.ArenaStatus;
import de.marcely.bedwars.api.event.arena.*;
import de.marcely.bedwars.api.event.player.*;
import lombok.RequiredArgsConstructor;
import me.infinity.groupstats.GroupStatsPlugin;
import me.infinity.groupstats.manager.PlayerStatsManager;
import me.infinity.groupstats.manager.ProxySyncManager;
import me.infinity.groupstats.models.GroupEnum;
import me.infinity.groupstats.models.StatKeys; // Import StatKeys
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.UUID;
// CompletableFuture might not be needed directly here if PlayerStatsManager handles async calls

/**
 * Listener for BedWars game events to update player statistics.
 * Uses {@link PlayerStatsManager} to record stats, which internally uses short stat keys
 * as defined in {@link me.infinity.groupstats.models.StatKeys}.
 * It also respects the server's configured group responsibility via {@link ProxySyncManager}.
 */
@RequiredArgsConstructor
public class GroupStatsListener implements Listener {

    private final PlayerStatsManager playerStatsManager;
    private final ProxySyncManager proxySyncManager;
    private final GroupStatsPlugin plugin; // For logging

    /**
     * Helper method to check if this server instance should record stats for the given game's group type.
     * @param gameGroupKey The group key of the game derived from arena settings (e.g., "SOLO", "DUOS").
     * @return True if stats should be recorded, false otherwise.
     */
    private boolean shouldRecordStatsForGroup(String gameGroupKey) {
        if (proxySyncManager.isLobbyServer()) {
            plugin.getLogger().finer(() -> "[GroupStatsListener] Currently on a lobby server. Skipping stat recording for game group: " + gameGroupKey);
            return false;
        }
        String serverGroupKey = proxySyncManager.getGroupServerKey();
        if (!serverGroupKey.equalsIgnoreCase(gameGroupKey)) {
            plugin.getLogger().finer(() -> "[GroupStatsListener] Server group key (" + serverGroupKey + ") does not match game group key (" + gameGroupKey + "). Skipping stat recording.");
            return false;
        }
        return true;
    }

    /**
     * Handles incrementing a player's stat, ensuring that the server is responsible for the game's group type.
     * The {@code statName} is expected to be a short key from {@link StatKeys}.
     * @param playerId The player's UUID.
     * @param statName The short key of the stat to increment (from {@link StatKeys}).
     * @param value The value to increment by.
     * @param contextGameGroupKey The group key of the current game context.
     */
    private void handleStatIncrement(UUID playerId, String statName, long value, String contextGameGroupKey) {
        if (!shouldRecordStatsForGroup(contextGameGroupKey)) return;
        playerStatsManager.incrementStat(playerId, statName, value)
            .exceptionally(ex -> {
                plugin.getLogger().warning("[GroupStatsListener] Failed to increment stat '" + statName + "' for player " + playerId + ": " + ex.getMessage());
                return null;
            });
    }


    @EventHandler
    public void onGameStart(RoundStartEvent event) {
        String gameGroupKey = GroupEnum.which(event.getArena().getPlayersPerTeam()).getJsonFormat();
        if (!shouldRecordStatsForGroup(gameGroupKey)) return;

        // gamesPlayed is typically incremented at the end of a game (loss/win)
        // or when a player is fully eliminated if that's the logic.
        // For RoundStart, we might ensure profiles are loaded if not done by join listener,
        // but PlayerJoinListener already calls getPlayerGroupStats.
        // If we need to ensure a player's hash exists in Redis for this group at game start:
        event.getArena().getPlayers().forEach(player -> {
            final UUID playerId = player.getUniqueId();
            // playerStatsManager.getPlayerGroupStats(playerId, gameGroupKey); // This ensures the hash is there, already done by join.
            // If we want to set a "lastPlayedGroup" type of stat:
            // playerStatsManager.setStat(playerId, "lastPlayedGroup", gameGroupKey);
            plugin.getLogger().finer(() -> "[GroupStatsListener] Game started for group " + gameGroupKey + ". Player: " + playerId);
        });
    }

    @EventHandler
    public void onTeamEliminated(TeamEliminateEvent event) {
        String gameGroupKey = GroupEnum.which(event.getArena().getPlayersPerTeam()).getJsonFormat();
        // This event implies a loss for all players on the eliminated team.
        // gamesPlayed is also incremented here.
        event.getArena().getPlayersInTeam(event.getTeam()).forEach(player -> {
            final UUID playerId = player.getUniqueId();
            handleStatIncrement(playerId, StatKeys.LOSSES, 1, gameGroupKey);
            handleStatIncrement(playerId, StatKeys.GAMES_PLAYED, 1, gameGroupKey); // Increment gamesPlayed on loss
            // Reset winstreak on loss.
            if (shouldRecordStatsForGroup(gameGroupKey)) {
                 playerStatsManager.setStat(playerId, StatKeys.WINSTREAK, "0")
                    .exceptionally(ex -> {
                        plugin.getLogger().warning("[GroupStatsListener] Failed to set stat '" + StatKeys.WINSTREAK + "' for player " + playerId + ": " + ex.getMessage());
                        return null;
                    });
            }
        });
    }

    @EventHandler
    public void onBedBreak(ArenaBedBreakEvent event) {
        String gameGroupKey = GroupEnum.which(event.getArena().getPlayersPerTeam()).getJsonFormat();

        // Handle bed breaker
        final UUID breakerId = event.getPlayer().getUniqueId();
        handleStatIncrement(breakerId, StatKeys.BEDS_BROKEN, 1, gameGroupKey);

        // Handle victims (team whose bed was broken)
        event.getArena().getPlayersInTeam(event.getTeam()).forEach(victim -> {
            final UUID victimId = victim.getUniqueId();
            handleStatIncrement(victimId, StatKeys.BEDS_LOST, 1, gameGroupKey);
        });
    }

    @EventHandler
    public void onPlayerKill(PlayerKillPlayerEvent event) {
        String gameGroupKey = GroupEnum.which(event.getArena().getPlayersPerTeam()).getJsonFormat();
        final boolean isFinalKill = event.getArena().isBedDestroyed(event.getArena().getPlayerTeam(event.getDamaged()));

        // Killer stats
        final UUID killerId = event.getKiller().getUniqueId();
        handleStatIncrement(killerId, isFinalKill ? StatKeys.FINAL_KILLS : StatKeys.KILLS, 1, gameGroupKey);

        // Victim stats
        final UUID victimId = event.getDamaged().getUniqueId();
        handleStatIncrement(victimId, isFinalKill ? StatKeys.FINAL_DEATHS : StatKeys.DEATHS, 1, gameGroupKey);

        // If it's a final death, the victim has effectively "lost" and completed their game participation.
        if (isFinalKill) {
            handleStatIncrement(victimId, StatKeys.GAMES_PLAYED, 1, gameGroupKey); // Increment gamesPlayed on final death
             // Reset winstreak on final death (loss)
            if (shouldRecordStatsForGroup(gameGroupKey)) {
                 playerStatsManager.setStat(victimId, StatKeys.WINSTREAK, "0")
                    .exceptionally(ex -> {
                        plugin.getLogger().warning("[GroupStatsListener] Failed to set stat '" + StatKeys.WINSTREAK + "' for victim " + victimId + ": " + ex.getMessage());
                        return null;
                    });
            }
        }
    }

    @EventHandler
    public void onGameEnd(RoundEndEvent event) {
        if (event.isTie()) return;

        String gameGroupKey = GroupEnum.which(event.getArena().getPlayersPerTeam()).getJsonFormat();

        // Handle winners
        event.getWinners().forEach(player -> {
            final UUID playerId = player.getUniqueId();
            handleStatIncrement(playerId, StatKeys.WINS, 1, gameGroupKey);
            handleStatIncrement(playerId, StatKeys.GAMES_PLAYED, 1, gameGroupKey); // Increment gamesPlayed on win

            if (shouldRecordStatsForGroup(gameGroupKey)) {
                playerStatsManager.incrementStat(playerId, StatKeys.WINSTREAK, 1)
                    .thenCompose(newWinstreak -> playerStatsManager.getPlayerGroupStats(playerId, gameGroupKey)) // Re-fetch to get current state including new winstreak
                    .thenAccept(stats -> {
                        if (stats != null) { // No need to check shouldRecordStatsForGroup again, already guarded
                            long currentWinstreak = stats.getLongStat(StatKeys.WINSTREAK);
                            long highestWinstreak = stats.getLongStat(StatKeys.HIGHEST_WINSTREAK);
                            if (currentWinstreak > highestWinstreak) {
                                playerStatsManager.setStat(playerId, StatKeys.HIGHEST_WINSTREAK, String.valueOf(currentWinstreak))
                                 .exceptionally(ex -> {
                                     plugin.getLogger().warning("[GroupStatsListener] Failed to set stat '" + StatKeys.HIGHEST_WINSTREAK + "' for player " + playerId + ": " + ex.getMessage());
                                     return null;
                                 });
                            }
                        }
                    })
                    .exceptionally(ex -> {
                        plugin.getLogger().warning("[GroupStatsListener] Failed to update stat '" + StatKeys.WINSTREAK + "' for player " + playerId + ": " + ex.getMessage());
                        return null;
                    });
            }
        });
    }

    @EventHandler
    public void onQuitArena(PlayerQuitArenaEvent event) { // Player leaves an arena mid-game
        if (event.getArena().getStatus() != ArenaStatus.RUNNING) return;
        if (GameAPI.get().isSpectator(event.getPlayer())) return;

        String gameGroupKey = GroupEnum.which(event.getArena().getPlayersPerTeam()).getJsonFormat();
        final UUID playerId = event.getPlayer().getUniqueId();

        handleStatIncrement(playerId, StatKeys.LOSSES, 1, gameGroupKey);
        handleStatIncrement(playerId, StatKeys.GAMES_PLAYED, 1, gameGroupKey);
        if (shouldRecordStatsForGroup(gameGroupKey)) {
            playerStatsManager.setStat(playerId, StatKeys.WINSTREAK, "0") // Reset winstreak
                .exceptionally(ex -> {
                    plugin.getLogger().warning("[GroupStatsListener] Failed to set stat '" + StatKeys.WINSTREAK + "' on arena quit for player " + playerId + ": " + ex.getMessage());
                    return null;
                });
        }
    }

    @EventHandler
    public void onPlayerIngameDeath(PlayerIngameDeathEvent event) { // For non-kill deaths like void, fall damage etc.
        if (event instanceof PlayerKillPlayerEvent) return;

        String gameGroupKey = GroupEnum.which(event.getArena().getPlayersPerTeam()).getJsonFormat();
        final UUID playerId = event.getPlayer().getUniqueId();

        boolean isFinalDeath = event.getArena().isBedDestroyed(event.getArena().getPlayerTeam(event.getPlayer()));

        handleStatIncrement(playerId, isFinalDeath ? StatKeys.FINAL_DEATHS : StatKeys.DEATHS, 1, gameGroupKey);

        if (isFinalDeath) {
            handleStatIncrement(playerId, StatKeys.GAMES_PLAYED, 1, gameGroupKey);
            if (shouldRecordStatsForGroup(gameGroupKey)) {
                 playerStatsManager.setStat(playerId, StatKeys.WINSTREAK, "0")
                    .exceptionally(ex -> {
                        plugin.getLogger().warning("[GroupStatsListener] Failed to set stat '" + StatKeys.WINSTREAK + "' on final death for player " + playerId + ": " + ex.getMessage());
                        return null;
                    });
            }
        }
    }
}