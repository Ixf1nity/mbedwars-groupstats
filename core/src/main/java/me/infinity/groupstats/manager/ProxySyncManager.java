package me.infinity.groupstats.manager;

import dev.dejvokep.boostedyaml.YamlDocument;
import lombok.Getter;
import me.infinity.groupstats.GroupStatsPlugin;

@Getter
public class ProxySyncManager {

    private final GroupStatsPlugin plugin;
    private boolean isLobbyServer;
    private String groupServerKey;

    public ProxySyncManager(GroupStatsPlugin plugin, YamlDocument config) {
        this.plugin = plugin;
        loadConfig(config);
    }

    private void loadConfig(YamlDocument config) {
        this.isLobbyServer = config.getBoolean("proxy-sync.is-lobby", false);
        this.groupServerKey = config.getString("proxy-sync.group-server", "SOLO").toUpperCase();

        if (this.groupServerKey.isEmpty() && !this.isLobbyServer) {
            plugin.getLogger().warning("[ProxySyncManager] 'group-server' is not defined in config.yml, and this is not a lobby server. Stats might not be saved correctly for a specific group.");
            // Default to a generic key if empty, though ideally configuration should be correct
            this.groupServerKey = "UNKNOWN_GROUP";
        }

        plugin.getLogger().info("[ProxySyncManager] Initialized. Lobby Server: " + isLobbyServer + ", Group Key: " + groupServerKey);
    }

    /**
     * Checks if the current server is configured as a lobby server.
     * Lobby servers typically do not handle or save group-specific player stats.
     *
     * @return true if this is a lobby server, false otherwise.
     */
    public boolean isLobbyServer() {
        return isLobbyServer;
    }

    /**
     * Gets the group key (e.g., SOLO, DUOS, QUADS) for which this server instance is responsible.
     * This key is used for namespacing stats in Redis and MongoDB.
     *
     * @return The group server key as a string. Returns "UNKNOWN_GROUP" if not properly configured and not a lobby server.
     */
    public String getGroupServerKey() {
        return groupServerKey;
    }
}
