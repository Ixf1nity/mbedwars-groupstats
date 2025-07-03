package me.infinity.groupstats.manager;

import com.google.gson.Gson;
import com.mongodb.client.MongoCollection;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.infinity.groupstats.models.GroupProfile;
import me.infinity.groupstats.GroupStatsPlugin;
// import me.infinity.groupstats.task.GroupUpdateTask; // Likely unused with Redis
import org.bson.Document;

// import java.util.Map; // Cache removed
import java.util.Optional;
import java.util.UUID;
// import java.util.concurrent.ConcurrentHashMap; // Cache removed

/**
 * Manages {@link GroupProfile} instances.
 * NOTE: This class is undergoing significant refactoring. With the introduction of Redis
 * for caching and live stat updates, the role of GroupManager is greatly diminished.
 * Its responsibilities for caching and direct saving/loading of full profiles are being
 * superseded by {@code PlayerStatsManager} (to be implemented) which will interact with
 * Redis and use more granular updates in {@link MongoStorage}.
 * This class may be further simplified or removed in the future.
 */
@Getter
@RequiredArgsConstructor
public class GroupManager {

    private final GroupStatsPlugin instance;
    private final Gson gson; // TODO: Evaluate if Gson is still needed here or if all (de)serialization moves to MongoStorage/PlayerStatsManager.
    private final MongoCollection<Document> profiles; // TODO: Evaluate if direct collection access is still needed or if MongoStorage encapsulates all.

    // Internal cache (Map<UUID, GroupProfile> cache) has been removed. Redis is now the caching layer.

    /**
     * Loads a {@link GroupProfile} directly from MongoDB.
     * This method is intended for potential use by {@code PlayerStatsManager} when initially
     * populating the Redis cache if a player's data is not yet in Redis.
     * <p>
     * It currently loads the entire player document as a {@code GroupProfile} object.
     * This might need adjustment if the {@code GroupProfile} structure diverges significantly
     * from the MongoDB document structure or if only partial data is needed for Redis seeding.
     * <p>
     * If no profile is found in MongoDB for the given UUID, a new {@code GroupProfile}
     * object is returned. The responsibility for persisting this new profile (if necessary)
     * will lie with the {@code PlayerStatsManager} through its Redis write-back mechanism.
     *
     * @param uniqueId The UUID of the player.
     * @return The {@code GroupProfile} loaded from MongoDB, or a new, non-persistent
     *         {@code GroupProfile} instance if not found.
     */
    public GroupProfile loadFromMongo(UUID uniqueId) {
        // Uses MongoStorage.loadData which deserializes the entire document into GroupProfile.
        // This assumes GroupProfile class structure matches the MongoDB document.
        Optional<GroupProfile> profileOpt = Optional.ofNullable(
                instance.getMongoStorage().loadData(uniqueId, GroupProfile.class)
        );

        if (profileOpt.isEmpty()) {
            GroupProfile newProfile = new GroupProfile(uniqueId);
            instance.getLogger().fine("[GroupManager] No profile found in MongoDB for " + uniqueId +
                    ". Returning a new GroupProfile instance. Persistence of new profiles is handled by PlayerStatsManager via Redis write-back.");
            return newProfile;
        }
        return profileOpt.get();
    }

    /**
     * Saves a {@link GroupProfile} directly to MongoDB, replacing the entire document.
     * <p>
     * <strong>DEPRECATED:</strong> This method is highly discouraged in the new Redis-based architecture.
     * Direct full-document saves can overwrite data updated by other servers or processes if not
     * carefully managed, and bypasses the Redis caching layer.
     * {@code PlayerStatsManager} should be responsible for persisting data from Redis to MongoDB
     * using granular updates provided by {@link MongoStorage#updateSpecificGroupData} or
     * {@link MongoStorage#incrementGroupStats}.
     * <p>
     * This method is retained temporarily for compatibility or specific edge cases but
     * should generally not be used for regular stat persistence.
     *
     * @param profile The {@link GroupProfile} to save. This will replace any existing document with the same UUID.
     */
    @Deprecated
    public void saveToMongo(GroupProfile profile) {
        instance.getLogger().warning("[GroupManager] Deprecated method saveToMongo(GroupProfile) was called for UUID: " + profile.getUniqueId() +
                ". This may cause data inconsistency with Redis. PlayerStatsManager should handle persistence.");
        // This uses the old MongoStorage.saveData method which replaces the entire document.
        instance.getMongoStorage().saveData(profile.getUniqueId(), profile, GroupProfile.class);
    }

    // The GroupUpdateTask (periodic save task) has been effectively removed as its
    // functionality (periodic persistence) will be re-evaluated and likely handled
    // by PlayerStatsManager in conjunction with Redis data.
}
