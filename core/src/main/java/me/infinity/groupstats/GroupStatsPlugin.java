package me.infinity.groupstats;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.dejvokep.boostedyaml.YamlDocument;
import lombok.Getter;
import me.infinity.groupstats.command.TestCommand;
import me.infinity.groupstats.listeners.GroupStatsListener;
import me.infinity.groupstats.listeners.ProfileJoinListener;
import me.infinity.groupstats.manager.GroupManager;
import me.infinity.groupstats.manager.MongoConnector;
import me.infinity.groupstats.manager.MongoStorage;
import me.infinity.groupstats.models.GroupProfile;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

@Getter
public final class GroupStatsPlugin extends JavaPlugin {

    private final Gson gson = new GsonBuilder()
            .serializeNulls()
            .excludeFieldsWithoutExposeAnnotation()
            .disableHtmlEscaping()
            .create();

    private YamlDocument configuration;
    private MongoConnector mongoConnector;
    private GroupManager groupManager;
    private MongoStorage<GroupProfile> mongoStorage;

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

        final int supportedAPIVersion = 203; // find the correct number in the tab "Table of API Versions"
        final String supportedVersionName = "5.5.3"; // update this accordingly to the number, otherwise the error will be wrong

        try {
            Class apiClass = Class.forName("de.marcely.bedwars.api.BedwarsAPI");
            int apiVersion = (int) apiClass.getMethod("getAPIVersion").invoke(null);

            if (apiVersion < supportedAPIVersion)
                throw new IllegalStateException();
        } catch(Exception e) {
            getLogger().warning("Sorry, your installed version of MBedwars is not supported. Please install at least v" + supportedVersionName);
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        // initialise mongo database;
        // Add debug logging
        this.getLogger().info("Initializing MongoDB connection...");
        this.mongoConnector = new MongoConnector(this, this.configuration);
        this.mongoConnector.init();
        
        if (this.mongoConnector.getProfiles() == null) {
            getLogger().severe("Failed to initialize MongoDB profiles collection!");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        
        this.getLogger().info("MongoDB connection successful, initializing GroupManager...");
        this.groupManager = new GroupManager(this, this.getGson(), mongoConnector.getProfiles());
        this.groupManager.init();

        this.mongoStorage = new MongoStorage<>(this.groupManager.getProfiles(), gson);

        this.getServer().getPluginCommand("groupstats").setExecutor(new TestCommand(this));

        this.getServer().getPluginManager().registerEvents(new ProfileJoinListener(this.groupManager), this);
        this.getServer().getPluginManager().registerEvents(new GroupStatsListener(this.groupManager), this);
    }

    @Override
    public void onDisable() {
        this.groupManager.getCache().values().forEach(groupManager::save);
        this.mongoConnector.shutdown();
        this.groupManager.getExecutorService().shutdown();
    }
}
