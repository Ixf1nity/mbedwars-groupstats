package me.infinity.groupstats;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.infinity.groupstats.models.GroupProfile;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@RequiredArgsConstructor
public class GroupStatsExpansion extends PlaceholderExpansion {

    @Getter
    private final GroupStatsPlugin instance;

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "groupstats";
    }

    @Override
    public @NotNull String getAuthor() {
        return "infinity";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.0";
    }

    @Override
    @SneakyThrows
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) {
            return null;
        }

        String[] args = params.split("_");
        if (args.length < 2 || args[0] == null || args[1] == null) {
            return "INVALID_PLACEHOLDER";
        }

        String groupName = args[0];
        StatisticType statisticType = StatisticType.fromString(args[1]);
        if (statisticType == null) {
            return "0";
        }

        // Attempt to get the profile from cache first for efficiency.
        GroupProfile profile = instance.getGroupManager().getCache().get(player.getUniqueId());

        if (profile == null) {
            // Profile not in cache, attempt to load from MongoDB.
            // This is a blocking call, suitable for PAPI's synchronous nature.
            // On game servers, profiles are usually cached by ProfileJoinListener.
            // On lobby servers, this will be a DB hit if not cached by other means.
            try {
                profile = instance.getMongoStorage().loadDataAsync(player.getUniqueId(), GroupProfile.class).get();
                if (profile == null) {
                    // Player has no data in the database.
                    // Create a new, empty profile for the purpose of these placeholder requests.
                    // This ensures that requests for stats from a non-existent profile correctly show "0"
                    // for individual stats, consistent with how an empty GroupNode would behave.
                    profile = new GroupProfile(player.getUniqueId());
                }
                // Optionally, put the loaded profile into the GroupManager's cache.
                // This could be beneficial even for lobby servers for frequently requested online players
                // to reduce DB load for subsequent requests for the same player during their session.
                // However, GroupManager's cache is primarily managed by ProfileJoinListener on game servers.
                // If is-lobby is true, GroupManager might not be actively populating this cache.
                // Consider the implications of cache size if many unique players are looked up.
                // instance.getGroupManager().getCache().put(player.getUniqueId(), profile);
            } catch (InterruptedException | ExecutionException e) {
                this.getInstance().getLogger().warning("Error fetching GroupProfile for PAPI: " + e.getMessage());
                // If DB error, create a temporary empty profile to avoid further errors and return "0" for stats.
                profile = new GroupProfile(player.getUniqueId());
                // return "ERROR_DB"; // Or let it proceed to return "0" for stats
            }
        }
        // Now, profile should ideally not be null. If it somehow is (e.g. constructor failed, though unlikely),
        // the subsequent .getStatistics() would NPE. The original code had a check here.
        // Given we instantiate a new GroupProfile if DB load returns null or on error,
        // profile should be non-null unless new GroupProfile() fails.
        // A final check for safety before calling methods on 'profile' is good.
        if (profile == null) {
             this.getInstance().getLogger().severe("Critical error: GroupProfile is null in PAPI expansion even after fallback.");
             return "ERROR_CRITICAL";
        }

        Map<String, GroupNode> stats = profile.getStatistics();

        if (groupName.equalsIgnoreCase("overAll")) {
            return handleOverallStats(stats, statisticType);
        }

        GroupNode groupNode = stats.get(groupName);
        if (groupNode == null) {
            return "0";
        }

        return handleGroupStats(groupNode, statisticType);
    }

    private String handleOverallStats(Map<String, GroupNode> stats, StatisticType type) {
        if (stats.isEmpty()) {
            return "0";
        }

        switch (type) {
            case GAMESPLAYED:
                return String.valueOf(stats.values().stream().mapToInt(value -> value.getGamesPlayed().get()).sum());
            case BEDSBROKEN:
                return String.valueOf(stats.values().stream().mapToInt(value -> value.getBedsBroken().get()).sum());
            case BEDSLOST:
                return String.valueOf(stats.values().stream().mapToInt(value -> value.getBedsLost().get()).sum());
            case KILLS:
                return String.valueOf(stats.values().stream().mapToInt(value -> value.getKills().get()).sum());
            case DEATHS:
                return String.valueOf(stats.values().stream().mapToInt(value -> value.getDeaths().get()).sum());
            case FINALKILLS:
                return String.valueOf(stats.values().stream().mapToInt(value -> value.getFinalKills().get()).sum());
            case FINALDEATHS:
                return String.valueOf(stats.values().stream().mapToInt(value -> value.getFinalDeaths().get()).sum());
            case WINS:
                return String.valueOf(stats.values().stream().mapToInt(value -> value.getWins().get()).sum());
            case LOSSES:
                return String.valueOf(stats.values().stream().mapToInt(value -> value.getLosses().get()).sum());
            case WINSTREAK:
                return String.valueOf(stats.values().stream().mapToInt(value -> value.getWinstreak().get()).sum());
            case HIGHESTWINSTREAK:
                return String.valueOf(stats.values().stream().mapToInt(value -> value.getHighestWinstreak().get()).max().orElse(0));
            case KDR:
                int kills = stats.values().stream().mapToInt(value -> value.getKills().get()).sum();
                int deaths = stats.values().stream().mapToInt(value -> value.getDeaths().get()).sum();
                return String.valueOf(getRatio(kills, deaths));
            case FKDR:
                int finalKills = stats.values().stream().mapToInt(value -> value.getFinalKills().get()).sum();
                int finalDeaths = stats.values().stream().mapToInt(value -> value.getFinalDeaths().get()).sum();
                return String.valueOf(getRatio(finalKills, finalDeaths));
            case BBLR:
                int bedsBroken = stats.values().stream().mapToInt(value -> value.getBedsBroken().get()).sum();
                int bedsLost = stats.values().stream().mapToInt(value -> value.getBedsLost().get()).sum();
                return String.valueOf(getRatio(bedsBroken, bedsLost));
            case WLR:
                int wins = stats.values().stream().mapToInt(value -> value.getWins().get()).sum();
                int losses = stats.values().stream().mapToInt(value -> value.getLosses().get()).sum();
                return String.valueOf(getRatio(wins, losses));
            default:
                return "0";
        }
    }

    private String handleGroupStats(GroupNode groupNode, StatisticType type) {
        switch (type) {
            case GAMESPLAYED:
                return String.valueOf(groupNode.getGamesPlayed().get());
            case BEDSBROKEN:
                return String.valueOf(groupNode.getBedsBroken().get());
            case BEDSLOST:
                return String.valueOf(groupNode.getBedsLost().get());
            case KILLS:
                return String.valueOf(groupNode.getKills().get());
            case DEATHS:
                return String.valueOf(groupNode.getDeaths().get());
            case FINALKILLS:
                return String.valueOf(groupNode.getFinalKills().get());
            case FINALDEATHS:
                return String.valueOf(groupNode.getFinalDeaths().get());
            case WINS:
                return String.valueOf(groupNode.getWins().get());
            case LOSSES:
                return String.valueOf(groupNode.getLosses().get());
            case WINSTREAK:
                return String.valueOf(groupNode.getWinstreak().get());
            case HIGHESTWINSTREAK:
                return String.valueOf(groupNode.getHighestWinstreak().get());
            case KDR:
                return String.valueOf(getRatio(groupNode.getKills().get(), groupNode.getDeaths().get()));
            case FKDR:
                return String.valueOf(getRatio(groupNode.getFinalKills().get(), groupNode.getFinalDeaths().get()));
            case BBLR:
                return String.valueOf(getRatio(groupNode.getBedsBroken().get(), groupNode.getBedsLost().get()));
            case WLR:
                return String.valueOf(getRatio(groupNode.getWins().get(), groupNode.getLosses().get()));
            default:
                return "0";
        }
    }

    private double getRatio(int numerator, int denominator) {
        if (denominator == 0) {
            return numerator;
        }
        double value = (double) numerator / denominator;
        return new BigDecimal(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private enum StatisticType {
        GAMESPLAYED, BEDSBROKEN, BEDSLOST, KILLS, DEATHS, FINALKILLS, FINALDEATHS, WINS, LOSSES, WINSTREAK, HIGHESTWINSTREAK, KDR, FKDR, BBLR, WLR;

        public static StatisticType fromString(String text) {
            if (text == null) {
                return null;
            }
            try {
                return StatisticType.valueOf(text.toUpperCase());
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }
}
