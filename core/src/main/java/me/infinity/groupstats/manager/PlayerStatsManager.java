package me.infinity.groupstats.manager;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import me.infinity.groupstats.GroupNode;
import me.infinity.groupstats.GroupStatsPlugin;
import me.infinity.groupstats.models.GroupProfile;
import me.infinity.groupstats.models.PlayerGroupStats;
import me.infinity.groupstats.models.StatKeys; // Import StatKeys

import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Manages player statistics by orchestrating operations between Redis (for caching and live updates)
 * and MongoDB (for persistence). It ensures that stat operations are performed asynchronously
 * and are targeted to specific server groups (e.g., SOLO, DUOS).
 * This manager uses canonical short stat keys defined in {@link me.infinity.groupstats.models.StatKeys}
 * for all Redis operations and when preparing data for MongoDB.
 */
public class PlayerStatsManager {

    private final GroupStatsPlugin plugin;
    private final RedisManager redisManager;
    private final MongoStorage<GroupProfile> mongoStorage; // To load/save full profiles if needed
    private final ProxySyncManager proxySyncManager;
    private final Gson gson; // For potential serialization/deserialization if not using raw maps

    private static final String REDIS_PLAYER_KEY_PREFIX = "player:";
    private static final Type GROUP_PROFILE_TYPE = new TypeToken<GroupProfile>() {}.getType();


    public PlayerStatsManager(GroupStatsPlugin plugin, RedisManager redisManager, MongoStorage<GroupProfile> mongoStorage, ProxySyncManager proxySyncManager) {
        this.plugin = plugin;
        this.redisManager = redisManager;
        this.mongoStorage = mongoStorage;
        this.proxySyncManager = proxySyncManager;
        this.gson = new Gson(); // Standard Gson, or plugin.getGson() if specific configs are needed
    }

    private String getRedisPlayerGroupKey(UUID playerUUID, String groupKey) {
        return REDIS_PLAYER_KEY_PREFIX + playerUUID.toString() + ":" + groupKey.toUpperCase();
    }

    /**
     * Asynchronously fetches a player's stats for a specific group.
     * 1. Tries to get all fields from the Redis hash for the player and group.
     * 2. If Redis has data, converts it to PlayerGroupStats and returns.
     * 3. If Redis is empty for this player/group, loads the full GroupProfile from MongoDB.
     * 4. Populates Redis with stats from all groups found in the MongoDB profile.
     * 5. Returns the PlayerGroupStats for the originally requested groupKey.
     *
     * @param playerUUID The UUID of the player.
     * @param groupKey   The specific group key (e.g., "SOLO") for which stats are requested.
     * @return A CompletableFuture containing {@link PlayerGroupStats} for the requested group,
     * or an empty PlayerGroupStats if no data is found after checking Redis and MongoDB.
     */
    public CompletableFuture<PlayerGroupStats> getPlayerGroupStats(UUID playerUUID, String groupKey) {
        if (!redisManager.isEnabled()) {
            plugin.getLogger().warning("[PlayerStatsManager] Attempted to getPlayerGroupStats while Redis is disabled for UUID: " + playerUUID);
            return CompletableFuture.completedFuture(new PlayerGroupStats(playerUUID, groupKey.toUpperCase())); // Return empty stats
        }

        String redisKey = getRedisPlayerGroupKey(playerUUID, groupKey);
        String upperGroupKey = groupKey.toUpperCase();

        return redisManager.hgetAll(redisKey).thenCompose(redisStats -> {
            if (redisStats != null && !redisStats.isEmpty()) {
                plugin.getLogger().fine("[PlayerStatsManager] Found stats in Redis for " + redisKey);
                return CompletableFuture.completedFuture(PlayerGroupStats.fromMap(playerUUID, upperGroupKey, redisStats));
            }

            // Not in Redis, or empty hash: Load from MongoDB
            plugin.getLogger().fine("[PlayerStatsManager] No stats in Redis for " + redisKey + ". Loading from MongoDB...");
            return mongoStorage.loadDataAsync(playerUUID, GROUP_PROFILE_TYPE).thenCompose(mongoProfile -> {
                if (mongoProfile == null || mongoProfile.getStatistics() == null || mongoProfile.getStatistics().isEmpty()) {
                    plugin.getLogger().fine("[PlayerStatsManager] No profile or GroupNode statistics found in MongoDB for " + playerUUID + ". Returning empty stats for group " + upperGroupKey);
                    return CompletableFuture.completedFuture(new PlayerGroupStats(playerUUID, upperGroupKey));
                }

                // Found profile in MongoDB, populate Redis for all groups (GroupNode) in the profile
                Map<String, GroupNode> mongoGroupNodes = mongoProfile.getStatistics(); // This is Map<String, GroupNode>
                List<CompletableFuture<Void>> redisPopulationFutures = mongoGroupNodes.entrySet().stream()
                    .map(entry -> {
                        String currentMongoGroupKey = entry.getKey().toUpperCase();
                        GroupNode groupNode = entry.getValue();
                        String currentRedisKey = getRedisPlayerGroupKey(playerUUID, currentMongoGroupKey);

                        Map<String, String> statsMapForRedis = convertGroupNodeToRedisMap(groupNode);

                        if (!statsMapForRedis.isEmpty()) {
                             plugin.getLogger().fine("[PlayerStatsManager] Populating Redis for " + currentRedisKey + " from MongoDB GroupNode.");
                            return redisManager.hmset(currentRedisKey, statsMapForRedis).thenAccept(ignored -> {});
                        }
                        return CompletableFuture.completedFuture(null);
                    })
                    .collect(Collectors.toList());

                return CompletableFuture.allOf(redisPopulationFutures.toArray(new CompletableFuture[0]))
                    .thenApply(ignored -> {
                        // After populating Redis, construct and return the PlayerGroupStats for the originally requested groupKey
                        GroupNode requestedNode = mongoGroupNodes.get(upperGroupKey); // Case-sensitive from Mongo keys
                        if (requestedNode == null) { // Try original case if not found with upper
                             requestedNode = mongoGroupNodes.get(groupKey);
                        }

                        if (requestedNode != null) {
                            return convertGroupNodeToPlayerGroupStats(playerUUID, upperGroupKey, requestedNode);
                        }
                        // If the specific group was not in the mongo profile after all.
                        return new PlayerGroupStats(playerUUID, upperGroupKey);
                    });
            });
        });
    }

    private Map<String, String> convertGroupNodeToRedisMap(GroupNode groupNode) {
        Map<String, String> redisMap = new HashMap<>();
        if (groupNode == null) return redisMap;

        // Assuming GroupNode fields are accessible (e.g. via Lombok's @Data or getters)
        // and they are AtomicIntegers
        redisMap.put(StatKeys.GAMES_PLAYED, String.valueOf(groupNode.getGamesPlayed().get()));
        redisMap.put(StatKeys.BEDS_BROKEN, String.valueOf(groupNode.getBedsBroken().get()));
        redisMap.put(StatKeys.BEDS_LOST, String.valueOf(groupNode.getBedsLost().get()));
        redisMap.put(StatKeys.KILLS, String.valueOf(groupNode.getKills().get()));
        redisMap.put(StatKeys.DEATHS, String.valueOf(groupNode.getDeaths().get()));
        redisMap.put(StatKeys.FINAL_KILLS, String.valueOf(groupNode.getFinalKills().get()));
        redisMap.put(StatKeys.FINAL_DEATHS, String.valueOf(groupNode.getFinalDeaths().get()));
        redisMap.put(StatKeys.WINS, String.valueOf(groupNode.getWins().get()));
        redisMap.put(StatKeys.LOSSES, String.valueOf(groupNode.getLosses().get()));
        redisMap.put(StatKeys.WINSTREAK, String.valueOf(groupNode.getWinstreak().get()));
        redisMap.put(StatKeys.HIGHEST_WINSTREAK, String.valueOf(groupNode.getHighestWinstreak().get()));
        return redisMap;
    }

    private PlayerGroupStats convertGroupNodeToPlayerGroupStats(UUID playerUUID, String groupKey, GroupNode groupNode) {
        Map<String, Object> statsMap = new HashMap<>();
        if (groupNode == null) return new PlayerGroupStats(playerUUID, groupKey); // Returns PlayerGroupStats with an empty map

        statsMap.put(StatKeys.GAMES_PLAYED, groupNode.getGamesPlayed().get());
        statsMap.put(StatKeys.BEDS_BROKEN, groupNode.getBedsBroken().get());
        statsMap.put(StatKeys.BEDS_LOST, groupNode.getBedsLost().get());
        statsMap.put(StatKeys.KILLS, groupNode.getKills().get());
        statsMap.put(StatKeys.DEATHS, groupNode.getDeaths().get());
        statsMap.put(StatKeys.FINAL_KILLS, groupNode.getFinalKills().get());
        statsMap.put(StatKeys.FINAL_DEATHS, groupNode.getFinalDeaths().get());
        statsMap.put(StatKeys.WINS, groupNode.getWins().get());
        statsMap.put(StatKeys.LOSSES, groupNode.getLosses().get());
        statsMap.put(StatKeys.WINSTREAK, groupNode.getWinstreak().get());
        statsMap.put(StatKeys.HIGHEST_WINSTREAK, groupNode.getHighestWinstreak().get());
        return new PlayerGroupStats(playerUUID, groupKey, statsMap);
    }

    /**
     * Asynchronously increments a numerical stat for a player in the current server's group.
     * This operation is ignored if the server is a lobby server or if Redis is disabled.
     *
     * @param playerUUID The UUID of the player.
     * @param statName   The short key of the stat to increment (from {@link StatKeys}).
     * @param value      The amount to increment by (can be negative to decrement).
     * @return A CompletableFuture that completes when the Redis operation is done, or immediately if no-op.
     */
    public CompletableFuture<Long> incrementStat(UUID playerUUID, String statName, long value) {
        if (proxySyncManager.isLobbyServer()) {
            plugin.getLogger().finer("[PlayerStatsManager] Lobby server; skipping stat increment for " + playerUUID + ", stat: " + statName);
            return CompletableFuture.completedFuture(0L); // No-op
        }
        if (!redisManager.isEnabled()) {
            plugin.getLogger().warning("[PlayerStatsManager] Attempted to incrementStat while Redis is disabled for UUID: " + playerUUID);
            return CompletableFuture.completedFuture(0L); // No-op
        }

        String groupKey = proxySyncManager.getGroupServerKey();
        String redisKey = getRedisPlayerGroupKey(playerUUID, groupKey);
        plugin.getLogger().finer("[PlayerStatsManager] Incrementing stat " + statName + " by " + value + " for " + redisKey);
        return redisManager.hincrBy(redisKey, statName, value);
    }

    /**
     * Asynchronously sets a string stat for a player in the current server's group.
     * This operation is ignored if the server is a lobby server or if Redis is disabled.
     *
     * @param playerUUID The UUID of the player.
     * @param statName   The short key of the stat to set (from {@link StatKeys}).
     * @param value      The string value of the stat.
     * @return A CompletableFuture that completes when the Redis operation is done, or immediately if no-op.
     */
    public CompletableFuture<Long> setStat(UUID playerUUID, String statName, String value) {
        if (proxySyncManager.isLobbyServer()) {
            plugin.getLogger().finer("[PlayerStatsManager] Lobby server; skipping stat set for " + playerUUID + ", stat: " + statName);
            return CompletableFuture.completedFuture(0L); // No-op
        }
        if (!redisManager.isEnabled()) {
            plugin.getLogger().warning("[PlayerStatsManager] Attempted to setStat while Redis is disabled for UUID: " + playerUUID);
            return CompletableFuture.completedFuture(0L); // No-op
        }

        String groupKey = proxySyncManager.getGroupServerKey();
        String redisKey = getRedisPlayerGroupKey(playerUUID, groupKey);
        plugin.getLogger().finer("[PlayerStatsManager] Setting stat " + statName + " to " + value + " for " + redisKey);
        return redisManager.hset(redisKey, statName, value);
    }

    /**
     * Asynchronously persists a player's stats for a specific group from Redis to MongoDB.
     * Fetches all fields (which are short stat keys) from the Redis hash,
     * attempts to convert numerical string values back to Long or Double,
     * and then uses {@link MongoStorage#updateSpecificGroupData(UUID, String, Map)} to save to MongoDB.
     * The map sent to MongoStorage will have short keys as defined in {@link StatKeys}.
     *
     * @param playerUUID The UUID of the player.
     * @param groupKey   The group key (e.g., "SOLO") whose stats should be persisted.
     * @return A CompletableFuture that completes when the MongoDB operation is done.
     */
    public CompletableFuture<Void> persistPlayerGroupStatsToDB(UUID playerUUID, String groupKey) {
        if (!redisManager.isEnabled()) {
            plugin.getLogger().warning("[PlayerStatsManager] Attempted to persistPlayerGroupStatsToDB while Redis is disabled for UUID: " + playerUUID);
            return CompletableFuture.completedFuture(null); // No-op
        }
        String upperGroupKey = groupKey.toUpperCase();
        String redisKey = getRedisPlayerGroupKey(playerUUID, upperGroupKey);

        plugin.getLogger().fine("[PlayerStatsManager] Persisting stats from Redis to MongoDB for " + redisKey);
        return redisManager.hgetAll(redisKey).thenCompose(redisStats -> {
            if (redisStats == null || redisStats.isEmpty()) {
                plugin.getLogger().fine("[PlayerStatsManager] No stats in Redis for " + redisKey + " to persist.");
                return CompletableFuture.completedFuture(null);
            }
            // Convert Map<String, String> from Redis to Map<String, Object> for MongoStorage.
            // This assumes stats are stored as strings in Redis and need appropriate conversion if Mongo expects numbers.
            // For now, let's assume PlayerGroupStats.fromMap handles types, or MongoStorage takes Map<String,String>
            // For simplicity, we'll pass Map<String, String> and let Mongo driver/MongoStorage handle it,
            // or ideally, convert to numeric types before sending to MongoStorage if they are numbers.
            // For this implementation, we'll convert to Map<String, Object> with basic number parsing.
            Map<String, Object> statsForMongo = new HashMap<>();
            for(Map.Entry<String, String> entry : redisStats.entrySet()){
                try {
                    // Attempt to parse Long/Double, fallback to String
                    if (entry.getValue().matches("-?\\d+")) { // Integer
                        statsForMongo.put(entry.getKey(), Long.parseLong(entry.getValue()));
                    } else if (entry.getValue().matches("-?\\d*\\.\\d+")) { // Decimal
                        statsForMongo.put(entry.getKey(), Double.parseDouble(entry.getValue()));
                    } else {
                        statsForMongo.put(entry.getKey(), entry.getValue());
                    }
                } catch (NumberFormatException e){
                    statsForMongo.put(entry.getKey(), entry.getValue()); // Fallback to string if parsing fails
                }
            }

            return mongoStorage.updateSpecificGroupData(playerUUID, upperGroupKey, statsForMongo);
        });
    }

    /**
     * Handles a player quitting a game server (not the entire proxy).
     * Persists the player's stats for the current server's group to MongoDB.
     *
     * @param playerUUID The UUID of the player who quit.
     * @return A CompletableFuture that completes when persistence is done.
     */
    public CompletableFuture<Void> handlePlayerQuit(UUID playerUUID) {
        if (proxySyncManager.isLobbyServer()) {
            return CompletableFuture.completedFuture(null); // Lobby servers don't manage group stats this way
        }
        String groupKey = proxySyncManager.getGroupServerKey();
        plugin.getLogger().fine("[PlayerStatsManager] Handling player quit for " + playerUUID + " on group " + groupKey + ". Persisting stats.");
        return persistPlayerGroupStatsToDB(playerUUID, groupKey);
    }
}
