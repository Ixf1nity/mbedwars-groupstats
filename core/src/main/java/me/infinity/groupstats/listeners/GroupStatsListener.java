package me.infinity.groupstats.listeners;

import com.j256.ormlite.dao.Dao;
import de.marcely.bedwars.api.GameAPI;
import de.marcely.bedwars.api.arena.ArenaStatus;
import de.marcely.bedwars.api.event.arena.*;
import de.marcely.bedwars.api.event.player.*;
import lombok.Getter;
import me.infinity.groupstats.GroupNode;
import me.infinity.groupstats.models.GroupEnum;
import me.infinity.groupstats.manager.DatabaseController;
import me.infinity.groupstats.models.StatisticType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.junit.internal.builders.JUnit3Builder;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Getter
public class GroupStatsListener implements Listener {

    private final DatabaseController databaseController;
    private final Dao<GroupNode, UUID> controller;

    public GroupStatsListener(DatabaseController databaseController) {
        this.databaseController = databaseController;
        this.controller = databaseController.getStatsController();
    }

    @EventHandler
    public void onBedBreak(ArenaBedBreakEvent event) {
        // Handle bed breaker
        final UUID breakerId = event.getPlayer().getUniqueId();
        CompletableFuture.runAsync(() -> {
            this.databaseController.incrementStatistic(breakerId, StatisticType.BEDSBROKEN);
        });

        // Handle victims
        event.getArena().getPlayersInTeam(event.getTeam()).forEach(victim -> {
            CompletableFuture.runAsync(() -> {
                this.databaseController.incrementStatistic(victim.getUniqueId(), StatisticType.BEDSLOST);
            });
        });
    }

    @EventHandler
    public void onPlayerKill(PlayerKillPlayerEvent event) {
        // Killer stats
        final UUID killerId = event.getKiller().getUniqueId();
        final UUID victimId = event.getDamaged().getUniqueId();
        CompletableFuture.runAsync(() -> {
            if (event.isFatalDeath()) {
                this.databaseController.incrementStatistic(killerId, StatisticType.FINALKILLS);
                this.databaseController.incrementStatistic(victimId, StatisticType.FINALDEATHS);
                this.databaseController.incrementStatistic(victimId, StatisticType.GAMESPLAYED);
            } else {
                this.databaseController.incrementStatistic(killerId, StatisticType.KILLS);
                this.databaseController.incrementStatistic(victimId, StatisticType.DEATHS);
            }
        });
    }

    @EventHandler
    public void onGameEnd(RoundEndEvent event) {
        if (event.isTie()) {
            return;
        }

        event.getWinners().forEach(player -> {
            final UUID playerId = player.getUniqueId();
            CompletableFuture.runAsync(() -> {
                this.databaseController.incrementStatistic(playerId, StatisticType.WINS);
                this.databaseController.incrementStatistic(playerId, StatisticType.GAMESPLAYED);
                this.databaseController.incrementStatistic(playerId, StatisticType.WINSTREAK);

                final int newWinstreak = this.databaseController.fetchStatistic(playerId, StatisticType.WINSTREAK);
                if (newWinstreak > this.databaseController.fetchStatistic(playerId, StatisticType.HIGHESTWINSTREAK)) {
                    this.databaseController.setStatistic(playerId, StatisticType.HIGHESTWINSTREAK, newWinstreak);
                }
                event.getLosers().forEach(uniqueId -> {
                    CompletableFuture.runAsync(() -> {
                       this.databaseController.incrementStatistic(uniqueId.getUniqueId(), StatisticType.LOSSES);
                       this.databaseController.setStatistic(uniqueId.getUniqueId(), StatisticType.WINSTREAK, 0);
                    });
                });
            });
        });
    }

    @EventHandler
    public void onQuitArena(PlayerQuitArenaEvent event) {
        if (event.getArena().getStatus() != ArenaStatus.RUNNING) return;
        if (GameAPI.get().isSpectator(event.getPlayer())) return;
        if (!event.getReason().isRageQuit()) return;

        final UUID playerId = event.getPlayer().getUniqueId();
        CompletableFuture.runAsync(() -> {
            this.databaseController.incrementStatistic(playerId, StatisticType.LOSSES);
            this.databaseController.setStatistic(playerId, StatisticType.WINSTREAK, 0);
        });
    }

    // handle void
    @EventHandler
    public void onPlayerDeath(PlayerIngameDeathEvent event) {
        if (event instanceof PlayerKillPlayerEvent) return;
        if (!(event.getPlayer().getLastDamageCause().getCause() == EntityDamageEvent.DamageCause.VOID)) return;

        CompletableFuture.runAsync(() -> {
            this.databaseController.incrementStatistic(event.getPlayer().getUniqueId(), StatisticType.DEATHS);
        });
    }
}