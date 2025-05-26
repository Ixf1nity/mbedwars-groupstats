package me.infinity.groupstats;

import com.google.gson.annotations.Expose;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.concurrent.atomic.AtomicInteger;

@Data
@Builder
@AllArgsConstructor
public class GroupNode {

    @Expose
    private AtomicInteger gamesPlayed, bedsBroken, bedsLost, kills, deaths, finalKills, finalDeaths, wins, losses, winstreak, highestWinstreak;

    public GroupNode() {
        this.gamesPlayed = new AtomicInteger(0);
        this.bedsBroken = new AtomicInteger(0);
        this.bedsLost = new AtomicInteger(0);
        this.kills = new AtomicInteger(0);
        this.deaths = new AtomicInteger(0);
        this.finalKills = new AtomicInteger(0);
        this.finalDeaths = new AtomicInteger(0);
        this.wins = new AtomicInteger(0);
        this.losses = new AtomicInteger(0);
        this.winstreak = new AtomicInteger(0);
        this.highestWinstreak = new AtomicInteger(0);
    }
}