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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

@Getter
public class GroupStatsListener implements Listener {

    private final GroupManager groupManager;

    private final Map<UUID, GroupProfile> cache;

    public GroupStatsListener(GroupManager groupManager) {
        this.groupManager = groupManager;
        this.cache = groupManager.getCache();
    }

    @EventHandler
    public void onGameStart(RoundStartEvent event) {
        final int playersPerTeam = event.getArena().getPlayersPerTeam();
        final GroupEnum groupEnum = GroupEnum.which(playersPerTeam);
        final String jsonFormat = groupEnum.getJsonFormat();

        event.getArena().getPlayers().forEach(player -> {
            final UUID playerId = player.getUniqueId();
            CompletableFuture.runAsync(() -> {
                cache.get(playerId)
                        .getStatistics()
                        .putIfAbsent(jsonFormat, new GroupNode());
            });
        });
    }
    
    @EventHandler
    public void onTeamEliminated(TeamEliminateEvent event) {
        final int playersPerTeam = event.getArena().getPlayersPerTeam();
        final GroupEnum groupEnum = GroupEnum.which(playersPerTeam);
        final String jsonFormat = groupEnum.getJsonFormat();
        event.getArena().getPlayersInTeam(event.getTeam()).forEach(player -> {
            final UUID playerId = player.getUniqueId();
            CompletableFuture.runAsync(() -> {
                GroupProfile profile = cache.get(playerId);
                if (profile != null) {
                    GroupNode stats = profile.getStatistics()
                            .computeIfAbsent(jsonFormat, k -> new GroupNode());

                    stats.getLosses().incrementAndGet();
                    stats.getWinstreak().set(0);
                }
            });
        });
    }

    @EventHandler
    public void onBedBreak(ArenaBedBreakEvent event) {
        final int playersPerTeam = event.getArena().getPlayersPerTeam();
        final GroupEnum groupEnum = GroupEnum.which(playersPerTeam);
        final String jsonFormat = groupEnum.getJsonFormat();

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
        final int playersPerTeam = event.getArena().getPlayersPerTeam();
        final GroupEnum groupEnum = GroupEnum.which(playersPerTeam);
        final String jsonFormat = groupEnum.getJsonFormat();
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
                    victimStats.getGamesPlayed().incrementAndGet();
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

        final int playersPerTeam = event.getArena().getPlayersPerTeam();
        final GroupEnum groupEnum = GroupEnum.which(playersPerTeam);
        final String jsonFormat = groupEnum.getJsonFormat();

        // Handle winners
        event.getWinners().forEach(player -> {
            final UUID playerId = player.getUniqueId();
            CompletableFuture.runAsync(() -> {
                GroupProfile profile = cache.get(playerId);
                if (profile != null) {
                    GroupNode stats = profile.getStatistics()
                            .computeIfAbsent(jsonFormat, k -> new GroupNode());

                    stats.getWins().incrementAndGet();
                    stats.getGamesPlayed().incrementAndGet();

                    final int newWinstreak = stats.getWinstreak().incrementAndGet();
                    if (newWinstreak > stats.getHighestWinstreak().get()) {
                        stats.getHighestWinstreak().set(newWinstreak);
                    }
                }
            });
        });
    }

    @EventHandler
    public void onQuitArena(PlayerQuitArenaEvent event) {
        if (event.getArena().getStatus() != ArenaStatus.RUNNING) return;
        if (GameAPI.get().isSpectator(event.getPlayer())) return;

        final int playersPerTeam = event.getArena().getPlayersPerTeam();
        final GroupEnum groupEnum = GroupEnum.which(playersPerTeam);
        final String jsonFormat = groupEnum.getJsonFormat();

        final UUID playerId = event.getPlayer().getUniqueId();
        CompletableFuture.runAsync(() -> {
            GroupProfile profile = cache.get(playerId);
            if (profile != null) {
                GroupNode stats = profile.getStatistics()
                        .computeIfAbsent(jsonFormat, k -> new GroupNode());

                stats.getLosses().incrementAndGet();
                stats.getWinstreak().set(0);
            }
        });
    }

    // handle void
    @EventHandler
    public void onPlayerDeath(PlayerIngameDeathEvent event) {
        if (event instanceof PlayerKillPlayerEvent) return;
        if (!(event.getPlayer().getLastDamageCause().getCause() == EntityDamageEvent.DamageCause.VOID)) return;

        final int playersPerTeam = event.getArena().getPlayersPerTeam();
        final GroupEnum groupEnum = GroupEnum.which(playersPerTeam);
        final String jsonFormat = groupEnum.getJsonFormat();

        CompletableFuture.runAsync(() -> {
            GroupProfile profile = this.cache.get(event.getPlayer().getUniqueId());
            GroupNode stats = profile.getStatistics()
                    .computeIfAbsent(jsonFormat, k -> new GroupNode());
            stats.getDeaths().incrementAndGet();
        });
    }
}