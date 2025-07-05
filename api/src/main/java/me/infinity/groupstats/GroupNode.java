package me.infinity.groupstats;

import com.j256.ormlite.field.DataType;
import com.j256.ormlite.field.DatabaseField;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
public class GroupNode {

    @DatabaseField(columnName = "uuid", id = true, dataType = DataType.UUID)
    private UUID uniqueId;

    @DatabaseField(columnName = "gp", dataType = DataType.INTEGER)
    private int gamesPlayed;

    @DatabaseField(columnName = "bb", dataType = DataType.INTEGER)
    private int bedsBroken;

    @DatabaseField(columnName = "bl", dataType = DataType.INTEGER)
    private int bedsLost;

    @DatabaseField(columnName = "k", dataType = DataType.INTEGER)
    private int kills;

    @DatabaseField(columnName = "d", dataType = DataType.INTEGER)
    private int deaths;

    @DatabaseField(columnName = "fk", dataType = DataType.INTEGER)
    private int finalKills;

    @DatabaseField(columnName = "fd", dataType = DataType.INTEGER)
    private int finalDeaths;

    @DatabaseField(columnName = "w", dataType = DataType.INTEGER)
    private int wins;

    @DatabaseField(columnName = "l", dataType = DataType.INTEGER)
    private int losses;

    @DatabaseField(columnName = "ws", dataType = DataType.INTEGER)
    private int winstreak;

    @DatabaseField(columnName = "hws", dataType = DataType.INTEGER)
    private int highestWinstreak;

    public GroupNode() { }

    public GroupNode(UUID uniqueId) {
        this.uniqueId = uniqueId;
        this.gamesPlayed = 0;
        this.bedsBroken = 0;
        this.bedsLost = 0;
        this.kills = 0;
        this.deaths = 0;
        this.finalKills = 0;
        this.finalDeaths = 0;
        this.wins = 0;
        this.losses = 0;
        this.winstreak = 0;
        this.highestWinstreak = 0;
    }
}