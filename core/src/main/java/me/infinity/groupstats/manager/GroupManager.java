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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Getter
@RequiredArgsConstructor
public class GroupManager {

    private final GroupStatsPlugin instance;
    private final Gson gson;
    private final MongoCollection<Document> profiles;

    private final Map<UUID, GroupProfile> cache = new ConcurrentHashMap<>();
    private ExecutorService executorService;

    public void init() {
        int updateTimer = instance.getConfiguration().getInt("UPDATE_TIMER", 5); // Default 5 minutes
        instance.getServer().getScheduler()
                .runTaskTimerAsynchronously(instance, new GroupUpdateTask(this), 60 * 20L, 20L * 60 * updateTimer);

        this.executorService = Executors.newFixedThreadPool(4);
    }

    public GroupProfile load(UUID uniqueId) {
        Optional<GroupProfile> profile = Optional.ofNullable(instance.getMongoStorage().loadData(uniqueId, GroupProfile.class));
        if (profile.isEmpty()) {
            instance.getMongoStorage().saveData(uniqueId, new GroupProfile(uniqueId), GroupProfile.class);
            return new GroupProfile(uniqueId);
        }
        return profile.get();
    }


    public void save(GroupProfile profile) {
        instance.getMongoStorage().saveData(profile.getUniqueId(), profile, GroupProfile.class);
    }
}
