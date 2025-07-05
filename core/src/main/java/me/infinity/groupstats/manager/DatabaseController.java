package me.infinity.groupstats.manager;

import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.dao.GenericRawResults;
import com.j256.ormlite.stmt.PreparedQuery;
import com.j256.ormlite.stmt.UpdateBuilder;
import lombok.Getter;
import me.infinity.groupstats.GroupNode;
import me.infinity.groupstats.GroupStatsPlugin;
import me.infinity.groupstats.models.StatisticType;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.UUID;

@Getter
public class DatabaseController {

    private final GroupStatsPlugin instance;
    private final Dao<GroupNode, UUID> statsController;

    public DatabaseController(GroupStatsPlugin instance) {
        this.instance = instance;
        this.statsController = this.instance.getDatabaseInitiator().getProfileDao();
    }

    public boolean upsert(UUID uniqueId) {
        try {
            this.statsController.createIfNotExists(new GroupNode(uniqueId));
        } catch (SQLException e) {
            this.instance.getLogger().severe("Failed to save group profile for " + uniqueId + ": " + e.getMessage());
            return false;
        }
        return true;
    }

    public int fetchStatistic(UUID uniqueId, StatisticType type) {
        try {
            PreparedQuery<GroupNode> rawQuery = statsController.queryBuilder()
                    .selectColumns(type.getColumn())
                    .where().idEq(uniqueId)
                    .prepare();

            GenericRawResults<String[]> rawResults = statsController.queryRaw(rawQuery.getStatement());
            return Integer.valueOf(Arrays.stream(rawResults.getFirstResult()).findFirst().get());
        } catch (SQLException e) {
            instance.getLogger().severe("Failed to fetch statistic column: " + e.getMessage());
            return 0;
        }
    }

    public int incrementStatistic(UUID uniqueId, StatisticType type) {
        try {
            UpdateBuilder<GroupNode, UUID> builder = statsController.updateBuilder();
            builder.where().idEq(uniqueId);
            builder.updateColumnValue(type.getColumn(), type.getColumn() + " + 1");
            return builder.update();
        } catch (SQLException e) {
            instance.getLogger().severe("Failed to increment statistics: " + e.getMessage());
            return 1;
        }
    }

    public int setStatistic(UUID uniqueId, StatisticType type, int value) {
        try {
            UpdateBuilder<GroupNode, UUID> builder = statsController.updateBuilder();
            builder.where().idEq(uniqueId);
            builder.updateColumnValue(type.getColumn(), value);
            return builder.update();
        } catch (SQLException e) {
            instance.getLogger().severe("Failed to increment statistics: " + e.getMessage());
            return 1;
        }
    }

}

