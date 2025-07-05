package me.infinity.groupstats;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.dejvokep.boostedyaml.YamlDocument;
import lombok.Getter;
import me.infinity.groupstats.listeners.GroupStatsListener;
import me.infinity.groupstats.listeners.ProfileJoinListener;
import me.infinity.groupstats.manager.DatabaseInitiator;
import me.infinity.groupstats.manager.DatabaseController;
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
    private DatabaseInitiator databaseInitiator;
    private DatabaseController databaseController;

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
        } catch (Exception e) {
            getLogger().warning("Sorry, your installed version of MBedwars is not supported. Please install at least v" + supportedVersionName);
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        this.getLogger().info("Initializing hikari connection...");
        this.databaseInitiator = new DatabaseInitiator(this);

        this.getLogger().info("Loading group manager...");
        this.databaseController = new DatabaseController(this);

        this.getLogger().info("Registering event listeners...");

        this.getServer().getPluginManager().registerEvents(new ProfileJoinListener(this.databaseController), this);
        this.getServer().getPluginManager().registerEvents(new GroupStatsListener(this.databaseController), this);

//        this.getLogger().info("Hooking with PAPI...");
//        new GroupStatsExpansion(this).register();
    }

    @Override
    public void onDisable() {
        this.databaseInitiator.disconnect();
    }
}
