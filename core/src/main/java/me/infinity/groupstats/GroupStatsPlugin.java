package me.infinity.groupstats;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.dejvokep.boostedyaml.YamlDocument;
import lombok.Getter;
import me.infinity.groupstats.manager.GroupManager;
import me.infinity.groupstats.manager.MongoManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.lang.reflect.Type;
import java.util.concurrent.ConcurrentHashMap;

@Getter
public final class GroupStatsPlugin extends JavaPlugin {

    private final Gson gson = new GsonBuilder()
            .serializeNulls()
            .excludeFieldsWithoutExposeAnnotation()
            .disableHtmlEscaping()
            .create();

    public static final Type STATISTIC_MAP_TYPE = new TypeToken<ConcurrentHashMap<String, GroupNode>>() {}.getType();

    private YamlDocument configuration;
    private MongoManager mongoManager;
    private GroupManager groupManager;

    @Override
    public void onLoad() {
        //initialise configuration;
        try {
            this.configuration = YamlDocument.create(
                    new File(this.getDataFolder(),
                            "config.yml"),
                    this.getResource("config.yml")
            );
        } catch (Exception ex) {
            this.getLogger().severe("Failed to initialise configuration.yml.");
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void onEnable() {
        // initialise mongo database;
        this.mongoManager = new MongoManager(this, this.configuration);
        this.mongoManager.init();

        this.groupManager = new GroupManager(this, mongoManager.getProfiles());
        this.groupManager.init();

    }

    @Override
    public void onDisable() {
        this.groupManager.getCache().values().forEach(groupManager::save);
        this.mongoManager.shutdown();
    }
}
