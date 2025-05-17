package me.infinity.groupstats.manager;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import me.infinity.groupstats.GroupProfile;
import me.infinity.groupstats.GroupStatsPlugin;
import me.infinity.groupstats.GroupUpdateTask;
import org.bson.Document;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static me.infinity.groupstats.GroupStatsPlugin.STATISTIC_MAP_TYPE;

@Getter
@RequiredArgsConstructor
public class GroupManager implements Listener {

    private final GroupStatsPlugin instance;
    private final MongoCollection<Document> profiles;

    private Map<UUID, GroupProfile> cache;

    public void init() {
        this.cache = new ConcurrentHashMap<>();

        int updateTimer = instance.getConfiguration().getInt("UPDATE-TICK");
        instance.getServer().getScheduler()
                .runTaskTimer(instance, new GroupUpdateTask(this), 60 * 20L, 20L * 60 * updateTimer);

        instance.getServer().getPluginManager().registerEvents(this, instance);
    }

    @SneakyThrows
    public GroupProfile load(UUID uniqueId) {
        GroupProfile groupProfile = new GroupProfile(uniqueId);
        Optional<Document> optionalDocument = Optional.ofNullable(
                profiles.find(
                        Filters.eq("uniqueId",
                                uniqueId)
                        )
                .first()
        );

        if (optionalDocument.isPresent()) {
            groupProfile.setStatistics(
                    instance.getGson().fromJson(
                            optionalDocument.get().getString("statistics"),
                            STATISTIC_MAP_TYPE)
            );
            return groupProfile;
        } else return save(groupProfile);
    }

    @SneakyThrows
    public GroupProfile save(GroupProfile profile) {
        Document document = Document.parse(instance.getGson().toJson(profile));
        this.profiles.replaceOne(
                Filters.eq("uniqueId", profile.getUniqueId()),
                document,
                new ReplaceOptions().upsert(true)
        );
        return profile;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        UUID uniqueId = event.getPlayer().getUniqueId();
        GroupProfile profile = this.load(uniqueId);
        this.cache.put(uniqueId, profile);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onQuit(PlayerQuitEvent event) {
        UUID uniqueId = event.getPlayer().getUniqueId();
        GroupProfile profile = this.cache.get(uniqueId);
        this.save(profile);
    }

}
