package me.infinity.groupstats;

import lombok.RequiredArgsConstructor;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.infinity.groupstats.manager.PlayerStatsManager; // Not directly used but good to be aware
import me.infinity.groupstats.manager.ProxySyncManager;
import me.infinity.groupstats.manager.RedisManager;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
// Removed unused imports like GroupProfile, Map, ExecutionException, SneakyThrows

/**
 * PlaceholderAPI Expansion for GroupStats.
 * Provides placeholders to display player statistics for different game groups.
 * Placeholders are expected to use short stat keys as defined in {@link me.infinity.groupstats.models.StatKeys}
 * (e.g., %groupstats_SOLO_k%, %groupstats_current_ws%).
 * Overall stats are currently not supported via this expansion due to performance considerations.
 */
@RequiredArgsConstructor
public class GroupStatsExpansion extends PlaceholderExpansion {

    private final GroupStatsPlugin plugin; // Keep for logging if needed, or get logger from managers
    private final RedisManager redisManager;
    private final ProxySyncManager proxySyncManager;
    // PlayerStatsManager is not directly used here as PAPI needs synchronous access,
    // which we'll achieve via RedisManager's hgetDirect.

    @Override
    public boolean persist() {
        return true; // Data is persisted in Redis/MongoDB
    }

    @Override
    public @NotNull String getIdentifier() {
        return "groupstats";
    }

    @Override
    public @NotNull String getAuthor() {
        return plugin.getDescription().getAuthors().toString(); // Or your name
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    private String getStatFromRedis(String playerUUID, String groupKey, String statName) {
        if (!redisManager.isEnabled()) {
            return "Redis Disabled";
        }
        String redisPlayerGroupKey = "player:" + playerUUID + ":" + groupKey.toUpperCase();
        return redisManager.hgetDirect(redisPlayerGroupKey, statName);
    }

    private long getLongStat(String playerUUID, String groupKey, String statName) {
        String value = getStatFromRedis(playerUUID, groupKey, statName);
        if (value == null) return 0L;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null || player.getUniqueId() == null) {
            return null; // Player is required
        }
        if (!redisManager.isEnabled()) {
            return "Redis N/A"; // Indicate Redis is not available
        }

        String[] args = params.split("_");
        if (args.length < 2) { // e.g., SOLO_kills
            return "Invalid Format";
        }

        String groupKeyParam = args[0];
        String statKey = args[1]; // This is the key for StatisticType enum

        String actualGroupKey;
        if ("current".equalsIgnoreCase(groupKeyParam)) {
            if (proxySyncManager.isLobbyServer()) {
                return "N/A on Lobby"; // Cannot get "current" group on a lobby server
            }
            actualGroupKey = proxySyncManager.getGroupServerKey();
        } else if ("overall".equalsIgnoreCase(groupKeyParam)) {
            // As decided, overall stats are not supported directly via this PAPI method for now
            // due to performance implications of calculating them synchronously.
            return "Overall N/A"; // Or "Unsupported"
        } else {
            actualGroupKey = groupKeyParam.toUpperCase(); // Use the specified group key
        }

        if (actualGroupKey == null || actualGroupKey.isEmpty() || "UNKNOWN_GROUP".equals(actualGroupKey)) {
            return "Invalid Group";
        }

        StatisticType statisticType = StatisticType.fromString(statKey);
        if (statisticType == null) {
            // If not a predefined StatisticType, try to fetch as a raw stat name
            String rawStatValue = getStatFromRedis(player.getUniqueId().toString(), actualGroupKey, statKey);
            return rawStatValue != null ? rawStatValue : "0";
        }

        String playerUUID = player.getUniqueId().toString();

        // Handle specific statistic types, including ratios
        switch (statisticType) {
            // Direct stats
            case GP:
            case BB:
            case BL:
            case K:
            case D:
            case FK:
            case FD:
            case W:
            case L:
            case WS:
            case HWS:
                String statValue = getStatFromRedis(playerUUID, actualGroupKey, statisticType.getActualKey());
                return statValue != null ? statValue : "0";

            // Ratio stats
            case KDR:
                long kills = getLongStat(playerUUID, actualGroupKey, StatKeys.KILLS);
                long deaths = getLongStat(playerUUID, actualGroupKey, StatKeys.DEATHS);
                return String.valueOf(getRatio(kills, deaths));
            case FKDR:
                long finalKills = getLongStat(playerUUID, actualGroupKey, StatKeys.FINAL_KILLS);
                long finalDeaths = getLongStat(playerUUID, actualGroupKey, StatKeys.FINAL_DEATHS);
                return String.valueOf(getRatio(finalKills, finalDeaths));
            case BBLR: // Bed Break/Loss Ratio
                long bedsBroken = getLongStat(playerUUID, actualGroupKey, StatKeys.BEDS_BROKEN);
                long bedsLost = getLongStat(playerUUID, actualGroupKey, StatKeys.BEDS_LOST);
                return String.valueOf(getRatio(bedsBroken, bedsLost));
            case WLR: // Win/Loss Ratio
                long wins = getLongStat(playerUUID, actualGroupKey, StatKeys.WINS);
                long losses = getLongStat(playerUUID, actualGroupKey, StatKeys.LOSSES);
                return String.valueOf(getRatio(wins, losses));
            default:
                return "Unknown Stat";
        }
    }

    private double getRatio(long numerator, long denominator) {
        if (denominator == 0) {
            return numerator; // Avoid division by zero, return numerator as per original logic
        }
        double value = (double) numerator / denominator;
        // Format to 2 decimal places
        return new BigDecimal(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    // Enum for stat types, ensures consistency in stat keys
    // These short keys MUST match those defined in StatKeys.java and used in Redis.
    /**
     * Enum representing the types of statistics that can be requested via placeholders.
     * It maps placeholder identifiers (like "k", "ws", "KDR") to their canonical short keys
     * or identifies them as ratio calculations.
     */
    private enum StatisticType {
        GP(StatKeys.GAMES_PLAYED),
        BB(StatKeys.BEDS_BROKEN),
        BL(StatKeys.BEDS_LOST),
        K(StatKeys.KILLS),
        D(StatKeys.DEATHS),
        FK(StatKeys.FINAL_KILLS),
        FD(StatKeys.FINAL_DEATHS),
        W(StatKeys.WINS),
        L(StatKeys.LOSSES),
        WS(StatKeys.WINSTREAK),
        HWS(StatKeys.HIGHEST_WINSTREAK),

        // Ratio types - their string representation in PAPI will be e.g. "KDR"
        // but they are composed of the short keys above.
        KDR("kdr_ratio"), // Special internal key for ratio, not a direct Redis key
        FKDR("fkdr_ratio"),
        BBLR("bblr_ratio"),
        WLR("wlr_ratio");

        private final String key;

        StatisticType(String key) {
            this.key = key;
        }

        /**
         * Gets the canonical short key used in Redis for direct stats.
         * For ratio stats, this returns the descriptive internal key (e.g., "kdr_ratio").
         * @return The short Redis key for direct stats, or an internal identifier for ratios.
         */
        public String getActualKey() {
            return key;
        }

        /**
         * Attempts to map a string identifier (from a PAPI placeholder) to a StatisticType.
         * It matches against the enum constant's name (case-insensitive, e.g., "KDR")
         * or, for direct stats, against its actual short key (case-insensitive, e.g., "k").
         * @param text The string identifier from the placeholder.
         * @return The matching StatisticType, or null if not found.
         */
        public static StatisticType fromString(String text) {
            if (text == null) return null;
            for (StatisticType type : StatisticType.values()) {
                // Match by enum name (e.g., PAPI placeholder "KDR" matches enum KDR)
                if (type.name().equalsIgnoreCase(text)) {
                    return type;
                }
                // For direct stats, allow matching by their short key if PAPI uses short keys
                if (!isRatioType(type) && type.getActualKey().equalsIgnoreCase(text)) {
                    return type;
                }
            }
            return null;
        }

        public static boolean isRatioType(StatisticType type) {
            return type == KDR || type == FKDR || type == BBLR || type == WLR;
        }
    }
}
