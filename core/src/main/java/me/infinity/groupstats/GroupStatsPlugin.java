package me.infinity.groupstats;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.dejvokep.boostedyaml.YamlDocument;
import lombok.Getter;
import me.infinity.groupstats.command.TestCommand;
import me.infinity.groupstats.listeners.GroupStatsListener;
import me.infinity.groupstats.listeners.ProfileJoinListener;
import me.infinity.groupstats.manager.GroupManager;
import me.infinity.groupstats.manager.MongoConnector;
import me.infinity.groupstats.manager.MongoStorage;
import me.infinity.groupstats.models.GroupEnum;
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

    // Configuration option: if true, server acts as a lobby, only providing PAPI placeholders.
    // If false, it's a game server, actively tracking stats for its configured server-group.
    private boolean isLobbyServer;
    // Configuration option: defines the specific game mode this server instance handles (e.g., SOLO, DUOS).
    private GroupEnum serverGroup;

    @Override
    public void onLoad() {
        //initialise configuration;
        try {
            this.configuration = YamlDocument.create(
                    new File(this.getDataFolder(),
                            "config.yml"),
                    this.getResource("config.yml")
            );
            // Load server type specific settings from config.yml
            this.isLobbyServer = this.configuration.getBoolean("is-lobby", false); // Default to false (game server)
            final String serverGroupString = this.configuration.getString("server-group", "SOLO").toUpperCase(); // Default to SOLO
            try {
                this.serverGroup = GroupEnum.valueOf(serverGroupString); // Convert string from config to GroupEnum
                this.getLogger().info("Server group successfully set to: " + this.serverGroup.name());
            } catch (IllegalArgumentException e) {
                this.getLogger().severe("Invalid 'server-group' value '" + serverGroupString + "' in config.yml. Valid values are: " +
                        java.util.Arrays.stream(GroupEnum.values()).map(GroupEnum::name).collect(java.util.stream.Collectors.joining(", ")) + ".");
                this.getLogger().severe("Defaulting to SOLO.");
                this.serverGroup = GroupEnum.SOLO;
            }

        } catch (Exception ex) {
            this.getLogger().severe("Failed to initialise configuration.yml.");
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void onEnable() {
        this.getLogger().info("Is Lobby Server: " + this.isLobbyServer);
        this.getLogger().info("Effective Server Group: " + this.serverGroup.name());

        final int supportedAPIVersion = 203; // find the correct number in the tab "Table of API Versions"
        final String supportedVersionName = "5.5.3"; // update this accordingly to the number, otherwise the error will be wrong

        try {
            Class apiClass = Class.forName("de.marcely.bedwars.api.BedwarsAPI");
            int apiVersion = (int) apiClass.getMethod("getAPIVersion").invoke(null);

            if (apiVersion < supportedAPIVersion)
                throw new IllegalStateException();
        } catch (Exception e) {
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

        this.getLogger().info("Loading group manager...");
        this.groupManager = new GroupManager(this, this.getGson(), mongoConnector.getProfiles());

        this.getLogger().info("Loading MongoDB controller...");
        this.mongoStorage = new MongoStorage<>(this.groupManager.getProfiles(), gson);

        this.getLogger().info("Registering test command...");
        this.getServer().getPluginCommand("gstest").setExecutor(new TestCommand(this));

        // Conditional listener registration based on 'is-lobby' config.
        // Game servers track stats; lobby servers only provide placeholders.
        if (!this.isLobbyServer) {
            this.getLogger().info("Registering game event listeners (server-type: GAME)...");
            this.getServer().getPluginManager().registerEvents(new ProfileJoinListener(this.groupManager, this), this);
            // Pass the configured serverGroup to the listener to scope its stat tracking.
            this.getServer().getPluginManager().registerEvents(new GroupStatsListener(this.groupManager, this, this.serverGroup), this);
        } else {
            this.getLogger().info("Skipping game event listeners (server-type: LOBBY)...");
        }

        this.getLogger().info("Hooking with PAPI (PlaceholderAPI)...");
        new GroupStatsExpansion(this).register();
    }

    @Override
    public void onDisable() {
        this.groupManager.getCache().values().forEach(groupManager::save);
        this.mongoConnector.shutdown();
    }
}
