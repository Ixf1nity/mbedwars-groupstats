package me.infinity.groupstats.manager;

import com.google.gson.Gson;
import com.mongodb.client.MongoCollection;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.infinity.groupstats.models.GroupProfile;
import me.infinity.groupstats.GroupStatsPlugin;
import me.infinity.groupstats.task.GroupUpdateTask;
import org.bson.Document;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Getter
@RequiredArgsConstructor
public class GroupManager {

    private final GroupStatsPlugin instance;
    private final Gson gson;
    private final MongoCollection<Document> profiles;
    private RedisCacheManager redisCacheManager;

    private final Map<UUID, GroupProfile> cache = new ConcurrentHashMap<>();

    public void init() {
        int updateTimer = instance.getConfiguration().getInt("UPDATE_TIMER", 5); // Default 5 minutes
        instance.getServer().getScheduler()
                .runTaskTimerAsynchronously(instance, new GroupUpdateTask(this), 60 * 20L, 20L * 60 * updateTimer);
        
        if (instance.getRedisConnector().isEnabled()) {
            this.redisCacheManager = new RedisCacheManager(
                instance.getRedisConnector().getJedisPool(),
                gson,
                instance.getConfiguration().getInt("REDIS.CACHE.TTL", 3600)
            );
        }
    }

    public GroupProfile load(UUID uniqueId) {
        if (redisCacheManager != null) {
            GroupProfile cached = redisCacheManager.getCachedProfile(uniqueId);
            if (cached != null) {
                return cached;
            }
        }
        
        Optional<GroupProfile> profile = Optional.ofNullable(instance.getMongoStorage().loadData(uniqueId, GroupProfile.class));
        if (profile.isEmpty()) {
            GroupProfile newProfile = new GroupProfile(uniqueId);
            instance.getMongoStorage().saveData(uniqueId, newProfile, GroupProfile.class);
            
            if (redisCacheManager != null) {
                redisCacheManager.cacheProfile(uniqueId, newProfile);
            }
            
            return newProfile;
        }
        
        GroupProfile loadedProfile = profile.get();
        
        if (redisCacheManager != null) {
            redisCacheManager.cacheProfile(uniqueId, loadedProfile);
        }
        
        return loadedProfile;
    }

    public void save(GroupProfile profile) {
        instance.getMongoStorage().saveData(profile.getUniqueId(), profile, GroupProfile.class);
        
        if (redisCacheManager != null) {
            redisCacheManager.cacheProfile(profile.getUniqueId(), profile);
            
            profile.getStatistics().forEach((gameMode, stats) -> {
                redisCacheManager.updateLeaderboards(profile.getUniqueId(), gameMode, stats);
            });
        }
    }
    
    public void removeFromCache(UUID uniqueId) {
        cache.remove(uniqueId);
        if (redisCacheManager != null) {
            redisCacheManager.removePlayer(uniqueId);
        }
    }
}
