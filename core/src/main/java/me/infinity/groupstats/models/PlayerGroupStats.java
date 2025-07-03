package me.infinity.groupstats.models;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Data Transfer Object representing a player's statistics for a specific game group (e.g., SOLO, DUOS).
 * This class is intended to be flexible and can store various stats as key-value pairs.
 * All stat keys used in the internal {@code stats} map and passed as {@code statName} parameters
 * are expected to be the canonical short keys defined in {@link StatKeys}.
 */
@Data
@NoArgsConstructor
public class PlayerGroupStats {

    private UUID playerUUID;
    private String groupKey; // e.g., "SOLO", "DUOS"
    /**
     * Flexible map to store various stats. Keys are expected to be short stat keys from {@link StatKeys}.
     */
    private Map<String, Object> stats;

    /**
     * Constructs an empty PlayerGroupStats object for a given player and group.
     * @param playerUUID The player's UUID.
     * @param groupKey The group key (e.g., "SOLO"). Stat keys within this object will be from {@link StatKeys}.
     */
    public PlayerGroupStats(UUID playerUUID, String groupKey) {
        this.playerUUID = playerUUID;
        this.groupKey = groupKey.toUpperCase();
        this.stats = new HashMap<>();
    }

    /**
     * Constructs a PlayerGroupStats object from an existing map of stats.
     * The keys in the provided stats map are assumed to be short stat keys from {@link StatKeys}.
     * @param playerUUID The player's UUID.
     * @param groupKey The group key.
     * @param stats A map containing stats, keyed by short stat keys.
     */
    public PlayerGroupStats(UUID playerUUID, String groupKey, Map<String, Object> stats) {
        this.playerUUID = playerUUID;
        this.groupKey = groupKey.toUpperCase();
        this.stats = new HashMap<>(stats); // Create a new map to avoid external modification issues
    }

    /**
     * Gets a stat value by its short key.
     * @param statName The short key of the stat (from {@link StatKeys}).
     * @return The stat value, or null if not found.
     */
    public Object getStat(String statName) {
        return stats.get(statName);
    }

    /**
     * Gets a stat value as an integer.
     * @param statName The short key of the stat (from {@link StatKeys}).
     * @return The integer value, or 0 if not found or not a number.
     */
    public int getIntStat(String statName) {
        Object val = stats.get(statName);
        if (val instanceof Number) {
            return ((Number) val).intValue();
        }
        try {
            return val != null ? Integer.parseInt(val.toString()) : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Gets a stat value as a long.
     * @param statName The short key of the stat (from {@link StatKeys}).
     * @return The long value, or 0L if not found or not a number.
     */
    public long getLongStat(String statName) {
        Object val = stats.get(statName);
        if (val instanceof Number) {
            return ((Number) val).longValue();
        }
        try {
            return val != null ? Long.parseLong(val.toString()) : 0L;
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /**
     * Gets a stat value as a double.
     * @param statName The short key of the stat (from {@link StatKeys}).
     * @return The double value, or 0.0 if not found or not a number.
     */
    public double getDoubleStat(String statName) {
        Object val = stats.get(statName);
        if (val instanceof Number) {
            return ((Number) val).doubleValue();
        }
        try {
            return val != null ? Double.parseDouble(val.toString()) : 0.0;
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    /**
     * Gets a stat value as a String.
     * @param statName The short key of the stat (from {@link StatKeys}).
     * @return The String value, or null if not found.
     */
    public String getStringStat(String statName) {
        Object val = stats.get(statName);
        return val != null ? val.toString() : null;
    }

    /**
     * Sets a stat value.
     * @param statName The short key of the stat (from {@link StatKeys}).
     * @param value The value to set.
     */
    public void setStat(String statName, Object value) {
        stats.put(statName, value);
    }

    /**
     * Increments a stat by an integer value. Assumes the stat is numerical.
     * @param statName The short key of the stat (from {@link StatKeys}).
     * @param value The integer value to add.
     */
    public void incrementStat(String statName, int value) {
        long current = getLongStat(statName);
        stats.put(statName, current + value);
    }

    /**
     * Increments a stat by a long value. Assumes the stat is numerical.
     * @param statName The short key of the stat (from {@link StatKeys}).
     * @param value The long value to add.
     */
    public void incrementStat(String statName, long value) {
        long current = getLongStat(statName);
        stats.put(statName, current + value);
    }

    /**
     * Increments a stat by a double value. Assumes the stat is numerical.
     * @param statName The short key of the stat (from {@link StatKeys}).
     * @param value The double value to add.
     */
    public void incrementStat(String statName, double value) {
        double current = getDoubleStat(statName);
        stats.put(statName, current + value);
    }

    /**
     * Converts this object's stats into a Map<String, String> suitable for Redis hmset.
     * All stat values are converted to their String representation.
     * @return A map of stat names to their stringified values.
     */
    public Map<String, String> toMap() {
        Map<String, String> stringMap = new HashMap<>();
        if (this.stats != null) {
            for (Map.Entry<String, Object> entry : this.stats.entrySet()) {
                if (entry.getValue() != null) {
                    stringMap.put(entry.getKey(), entry.getValue().toString());
                }
            }
        }
        return stringMap;
    }

    /**
     * Creates a PlayerGroupStats instance from a Map<String, String> (typically from Redis hgetAll).
     * Attempts to parse numerical values back into Long or Double where appropriate.
     *
     * @param playerUUID The player's UUID.
     * @param groupKey   The group key.
     * @param redisMap   The map retrieved from Redis.
     * @return A new PlayerGroupStats instance.
     */
    public static PlayerGroupStats fromMap(UUID playerUUID, String groupKey, Map<String, String> redisMap) {
        PlayerGroupStats playerStats = new PlayerGroupStats(playerUUID, groupKey);
        if (redisMap != null) {
            for (Map.Entry<String, String> entry : redisMap.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                try {
                    if (value.contains(".")) {
                        playerStats.setStat(key, Double.parseDouble(value));
                    } else {
                        playerStats.setStat(key, Long.parseLong(value));
                    }
                } catch (NumberFormatException e) {
                    playerStats.setStat(key, value); // Fallback to string if not a number
                }
            }
        }
        return playerStats;
    }
}
