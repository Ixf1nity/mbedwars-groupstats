package me.infinity.groupstats.listeners;

import de.marcely.bedwars.api.GameAPI;
import de.marcely.bedwars.api.arena.ArenaStatus;
import de.marcely.bedwars.api.event.arena.*;
import de.marcely.bedwars.api.event.player.*;
import lombok.Getter;
import me.infinity.groupstats.GroupNode;
import me.infinity.groupstats.models.GroupEnum;
import me.infinity.groupstats.models.GroupProfile;
import me.infinity.groupstats.manager.GroupManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Getter
public class GroupStatsListener implements Listener {

    private final GroupManager groupManager;
    private final GroupStatsPlugin plugin;
    // The specific server group (e.g., SOLO, DUOS) this listener instance is configured to handle stats for.
    private final GroupEnum serverGroup;
    private final Map<UUID, GroupProfile> cache;
    // Tracks UUIDs of players who started the current game matching this server's configured serverGroup.
    // Used for accurate game played, win/loss accounting, especially if players leave mid-game.
    private final Set<UUID> participatingPlayersInCurrentGame;

    public GroupStatsListener(GroupManager groupManager, GroupStatsPlugin plugin, GroupEnum serverGroup) {
        this.groupManager = groupManager;
        this.plugin = plugin;
        this.serverGroup = serverGroup; // Injected from GroupStatsPlugin, based on config.yml
        this.cache = groupManager.getCache();
        this.participatingPlayersInCurrentGame = new HashSet<>();
    }

    /**
     * Handles the start of a game round.
     * If the game's group type matches the server's configured group type:
     * - Clears the set of participating players for the new game.
     * - Adds all players in the arena to the participating set.
     * - Ensures a GroupNode exists for each participant for the current serverGroup.
     */
    @EventHandler
    public void onGameStart(RoundStartEvent event) {
        final GroupEnum eventGroupEnum = GroupEnum.which(event.getArena().getPlayersPerTeam());
        // Only process if the event's game type matches this server's configured group type.
        if (eventGroupEnum != this.serverGroup) {
            return;
        }
        // Clear participants from any previous game. Important for handling back-to-back games or missed RoundEndEvents.
        this.participatingPlayersInCurrentGame.clear();
        final String jsonFormat = this.serverGroup.getJsonFormat(); // Use the configured server group's JSON key

        event.getArena().getPlayers().forEach(player -> {
            final UUID playerId = player.getUniqueId();
            this.participatingPlayersInCurrentGame.add(playerId); // Add player to the set of current game participants.
            CompletableFuture.runAsync(() -> {
                GroupProfile profile = cache.get(playerId);
                if (profile != null) {
                    profile.getStatistics()
                           .putIfAbsent(jsonFormat, new GroupNode());
                }
            });
        });
    }

    /**
     * Handles team elimination.
     * Losses are no longer recorded here directly; they are determined at RoundEndEvent.
     * This event is filtered by serverGroup for consistency, though no stats are modified.
     */
    @EventHandler
    public void onTeamEliminated(TeamEliminateEvent event) {
        final GroupEnum eventGroupEnum = GroupEnum.which(event.getArena().getPlayersPerTeam());
        if (eventGroupEnum != this.serverGroup) {
            return;
        }
        // final String jsonFormat = this.serverGroup.getJsonFormat(); // Not strictly needed as no stats are directly modified here

        event.getArena().getPlayersInTeam(event.getTeam()).forEach(player -> {
            // final UUID playerId = player.getUniqueId();
            // Team elimination itself doesn't mean a loss for the player if their overall team can still win
            // or if they've already left. Win/loss is determined at the end of the game for all participants.
        });
    }

    @EventHandler
    public void onBedBreak(ArenaBedBreakEvent event) {
        final GroupEnum eventGroupEnum = GroupEnum.which(event.getArena().getPlayersPerTeam());
        if (eventGroupEnum != this.serverGroup) {
            return; // Not the configured group type for this server
        }
        final String jsonFormat = this.serverGroup.getJsonFormat();

        // Handle bed breaker
        final UUID breakerId = event.getPlayer().getUniqueId();
        CompletableFuture.runAsync(() -> {
            GroupProfile breakerProfile = cache.get(breakerId);
            if (breakerProfile != null) {
                GroupNode stats = breakerProfile.getStatistics()
                        .computeIfAbsent(jsonFormat, k -> new GroupNode());
                stats.getBedsBroken().incrementAndGet();
            }
        });

        // Handle victims
        event.getArena().getPlayersInTeam(event.getTeam()).forEach(victim -> {
            final UUID victimId = victim.getUniqueId();
            CompletableFuture.runAsync(() -> {
                GroupProfile victimProfile = cache.get(victimId);
                if (victimProfile != null) {
                    GroupNode stats = victimProfile.getStatistics()
                            .computeIfAbsent(jsonFormat, k -> new GroupNode());
                    stats.getBedsLost().incrementAndGet();
                }
            });
        });
    }

    @EventHandler
    public void onPlayerKill(PlayerKillPlayerEvent event) {
        final GroupEnum eventGroupEnum = GroupEnum.which(event.getArena().getPlayersPerTeam());
        if (eventGroupEnum != this.serverGroup) {
            return; // Not the configured group type for this server
        }
        final String jsonFormat = this.serverGroup.getJsonFormat();
        final boolean isFinalKill = event.getArena().isBedDestroyed(
                event.getArena().getPlayerTeam(event.getDamaged())
        );

        // Killer stats
        final UUID killerId = event.getKiller().getUniqueId();
        CompletableFuture.runAsync(() -> {
            GroupProfile killerProfile = cache.get(killerId);
            if (killerProfile != null) {
                GroupNode killerStats = killerProfile.getStatistics()
                        .computeIfAbsent(jsonFormat, k -> new GroupNode());

                if (isFinalKill) {
                    killerStats.getFinalKills().incrementAndGet();
                } else {
                    killerStats.getKills().incrementAndGet();
                }
            }
        });

        // Victim stats
        final UUID victimId = event.getDamaged().getUniqueId();
        CompletableFuture.runAsync(() -> {
            GroupProfile victimProfile = cache.get(victimId);
            if (victimProfile != null) {
                GroupNode victimStats = victimProfile.getStatistics()
                        .computeIfAbsent(jsonFormat, k -> new GroupNode());

                if (isFinalKill) {
                    victimStats.getFinalDeaths().incrementAndGet();
                    // victimStats.getGamesPlayed().incrementAndGet(); // Moved to RoundEndEvent
                } else {
                    victimStats.getDeaths().incrementAndGet();
                }
            }
        });
    }

    @EventHandler
    public void onGameEnd(RoundEndEvent event) {
        if (event.isTie()) {
            return;
        }

        final GroupEnum eventGroupEnum = GroupEnum.which(event.getArena().getPlayersPerTeam());
        if (eventGroupEnum != this.serverGroup) {
            return; // Not the configured group type for this server
        }
        final String jsonFormat = this.serverGroup.getJsonFormat(); // Key for stats map
        final Set<UUID> winnerUuids = new HashSet<>();
        event.getWinners().forEach(player -> winnerUuids.add(player.getUniqueId())); // Collect winners for quick lookup

        // Iterate over all players who were recorded as participants at the start of this game.
        // This ensures that players who left mid-game are still accounted for in stats.
        for (UUID playerId : this.participatingPlayersInCurrentGame) {
            CompletableFuture.runAsync(() -> {
                GroupProfile profile = cache.get(playerId); // Get player's cached profile
                if (profile == null) {
                    // Should not happen if player was added during onGameStart and ProfileJoinListener works
                    plugin.getLogger().warning("GroupProfile not found in cache for " + playerId + " during RoundEndEvent.");
                    return;
                }

                GroupNode stats = profile.getStatistics()
                        .computeIfAbsent(jsonFormat, k -> new GroupNode());

                stats.getGamesPlayed().incrementAndGet(); // Increment games played for all participants

                if (winnerUuids.contains(playerId)) {
                    // Player is a winner
                    stats.getWins().incrementAndGet();
                    final int newWinstreak = stats.getWinstreak().incrementAndGet();
                    if (newWinstreak > stats.getHighestWinstreak().get()) {
                        stats.getHighestWinstreak().set(newWinstreak);
                    }
                } else {
                    // Player was a participant but not a winner, so record a loss.
                    stats.getLosses().incrementAndGet();
                    stats.getWinstreak().set(0); // Reset winstreak on a loss.
                }
            });
        }

        this.participatingPlayersInCurrentGame.clear(); // Reset participant set for the next game.
    }

    /**
     * Handles player quitting an arena.
     * No direct stat changes are made here. If the player was participating in a game
     * matching the serverGroup, their stats (win/loss/gamesPlayed) will be updated
     * in RoundEndEvent based on the game's outcome.
     */
    @EventHandler
    public void onQuitArena(PlayerQuitArenaEvent event) {
        if (event.getArena().getStatus() != ArenaStatus.RUNNING) return;
        if (GameAPI.get().isSpectator(event.getPlayer())) return;

        final GroupEnum eventGroupEnum = GroupEnum.which(event.getArena().getPlayersPerTeam());
        if (eventGroupEnum != this.serverGroup) {
            return; // Only consider quits from games matching the configured server group.
        }
        // Player remains in 'participatingPlayersInCurrentGame' if they were in it.
        // Their win/loss is determined when RoundEndEvent fires for that game.
    }

    // handle void for players dying to void not by another player
    @EventHandler
    public void onPlayerDeath(PlayerIngameDeathEvent event) {
        if (event instanceof PlayerKillPlayerEvent) return;
        if (event.getPlayer().getLastDamageCause() == null || // Add null check for safety
            !(event.getPlayer().getLastDamageCause().getCause() == EntityDamageEvent.DamageCause.VOID)) return;

        final GroupEnum eventGroupEnum = GroupEnum.which(event.getArena().getPlayersPerTeam());
        if (eventGroupEnum != this.serverGroup) {
            return; // Not the configured group type for this server
        }
        final String jsonFormat = this.serverGroup.getJsonFormat();

        CompletableFuture.runAsync(() -> {
            GroupProfile profile = this.cache.get(event.getPlayer().getUniqueId());
            GroupNode stats = profile.getStatistics()
                    .computeIfAbsent(jsonFormat, k -> new GroupNode());
            stats.getDeaths().incrementAndGet();
        });
    }
}