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
import me.infinity.groupstats.manager.PlayerStatsManager; // Added import
import me.infinity.groupstats.manager.ProxySyncManager;
import me.infinity.groupstats.manager.RedisManager;
import me.infinity.groupstats.models.GroupProfile;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player; // Added import for onDisable
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

/**
 * Main class for the GroupStats plugin.
 * Handles plugin lifecycle, loading configurations, initializing managers,
 * registering commands, listeners, and PlaceholderAPI expansion.
 * This plugin is being refactored to support proxy-wide stat synchronization
 * using Redis for caching and MongoDB for persistence.
 */
@Getter
public final class GroupStatsPlugin extends JavaPlugin {

    // Gson instance for JSON serialization/deserialization, configured for specific needs.
    private final Gson gson = new GsonBuilder()
            .serializeNulls()
            .excludeFieldsWithoutExposeAnnotation()
            .disableHtmlEscaping()
            .create();

    private YamlDocument configuration;
    private MongoConnector mongoConnector;
    private GroupManager groupManager; // This will likely be replaced or refactored
    private MongoStorage<GroupProfile> mongoStorage; // This might also be adjusted
    private ProxySyncManager proxySyncManager;
    private RedisManager redisManager;
    private PlayerStatsManager playerStatsManager;

    /**
     * Called when the plugin is loaded by Bukkit.
     * Initializes the configuration file (config.yml).
     */
    @Override
    public void onLoad() {
        //initialise configuration;
        this.getLogger().info("Loading configuration file...");
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

    /**
     * Called when the plugin is enabled by Bukkit.
     * Performs version checks for dependencies (MBedwars), initializes database connections (MongoDB),
     * managers (ProxySyncManager, RedisManager, PlayerStatsManager), commands, listeners, and PAPI expansion.
     * Handles graceful shutdown if critical components fail to initialize.
     */
    @Override
    public void onEnable() {
        this.getLogger().info("Enabling GroupStats plugin...");

        // MBedwars API version check
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

        if (this.mongoConnector.getProfiles() == null) { // TODO: Re-evaluate if getProfiles() is still the right check after Redis integration
            getLogger().severe("Failed to initialize MongoDB profiles collection!");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        // Initialize ProxySyncManager
        this.getLogger().info("Initializing ProxySyncManager...");
        this.proxySyncManager = new ProxySyncManager(this, this.configuration);

        // Initialize RedisManager
        this.getLogger().info("Initializing RedisManager...");
        this.redisManager = new RedisManager(this, this.configuration);
        if (!this.redisManager.isEnabled()) { // Check isEnabled which covers connection success
            getLogger().severe("RedisManager failed to initialize or connect. Check Redis server and config. Disabling plugin.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        // Initialize MongoStorage (might be simplified or its role adjusted with Redis)
        this.getLogger().info("Loading MongoDB storage controller...");
        // The collection might come directly from MongoConnector if GroupManager is removed/changed
        this.mongoStorage = new MongoStorage<>(this.mongoConnector.getProfiles(), gson);

        // Initialize PlayerStatsManager
        this.getLogger().info("Initializing PlayerStatsManager...");
        this.playerStatsManager = new PlayerStatsManager(this, this.redisManager, this.mongoStorage, this.proxySyncManager);

        // Old GroupManager initialization - to be replaced/removed or used minimally
        this.getLogger().info("Initializing GroupManager (legacy, for transition)...");
        this.groupManager = new GroupManager(this, this.getGson(), mongoConnector.getProfiles());


        this.getLogger().info("Registering test command...");
        // Pass PlayerStatsManager and ProxySyncManager to TestCommand
        this.getServer().getPluginCommand("gstest").setExecutor(new TestCommand(this, this.playerStatsManager, this.proxySyncManager));

        this.getLogger().info("Registering event listeners...");
        // Pass PlayerStatsManager and ProxySyncManager to listeners
        this.getServer().getPluginManager().registerEvents(new ProfileJoinListener(this.playerStatsManager, this.proxySyncManager, this), this);
        this.getServer().getPluginManager().registerEvents(new GroupStatsListener(this.playerStatsManager, this.proxySyncManager, this), this);

        this.getLogger().info("Hooking with PAPI...");
        // Pass RedisManager and ProxySyncManager to PAPI expansion
        new GroupStatsExpansion(this, this.redisManager, this.proxySyncManager).register();
        this.getLogger().info("GroupStats plugin enabled successfully.");
    }

    /**
     * Called when the plugin is disabled by Bukkit.
     * Handles graceful shutdown of resources, such as database connections (MongoDB, Redis)
     * and saving any pending data if necessary (though this responsibility will largely
     * shift to PlayerStatsManager and its interaction with Redis).
     */
    @Override
    public void onDisable() {
        this.getLogger().info("Disabling GroupStats plugin...");

        // Old saving logic - this will change with PlayerStatsManager and Redis.
        // PlayerStatsManager should ideally handle flushing any necessary Redis data to MongoDB
        // on game end or player proxy quit, rather than a bulk save here.
        // However, as a fallback, persist stats for any currently online players.
        if (playerStatsManager != null && !proxySyncManager.isLobbyServer()) {
            this.getLogger().info("Persisting stats for online players before shutdown...");
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player != null) {
                    UUID playerUUID = player.getUniqueId();
                    this.getLogger().fine("Triggering persistence for " + player.getName() + " on disable.");
                    playerStatsManager.handlePlayerQuit(playerUUID)
                        .exceptionally(ex -> {
                            this.getLogger().severe("Error persisting stats for " + player.getName() + " during disable: " + ex.getMessage());
                            return null;
                        });
                }
            }
            // Give a brief moment for async operations to be dispatched, though not guaranteed to complete.
            try {
                Thread.sleep(500); // Small delay, adjust as needed or manage futures for completion.
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                this.getLogger().warning("Interrupted during onDisable stat persistence delay.");
            }
        } else if (proxySyncManager != null && proxySyncManager.isLobbyServer()){
             this.getLogger().info("Lobby server, skipping on-disable persistence for group stats.");
        }


        if (this.groupManager != null && this.groupManager.getCache() != null) { // Old logic, to be removed eventually
            this.getLogger().info("Note: Old GroupManager cache saving on disable is being phased out.");
        }

        if (this.redisManager != null && this.redisManager.isEnabled()) {
            this.getLogger().info("Shutting down RedisManager...");
            this.redisManager.shutdown();
        }

        if (this.mongoConnector != null) {
            this.getLogger().info("Shutting down MongoConnector...");
            this.mongoConnector.shutdown();
        }
        this.getLogger().info("GroupStats plugin fully disabled.");
    }

    /**
     * Gets the {@link ProxySyncManager} instance.
     * @return The ProxySyncManager.
     */
    public ProxySyncManager getProxySyncManager() {
        return proxySyncManager;
    }

    /**
     * Gets the {@link RedisManager} instance.
     * @return The RedisManager.
     */
    public RedisManager getRedisManager() {
        return redisManager;
    }

    // TODO: Add getters for PlayerStatsManager once it's implemented
    public PlayerStatsManager getPlayerStatsManager() {
        return playerStatsManager;
    }
}
