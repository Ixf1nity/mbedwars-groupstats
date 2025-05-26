package me.infinity.groupstats.manager;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import dev.dejvokep.boostedyaml.YamlDocument;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.infinity.groupstats.GroupStatsPlugin;
import org.bson.Document;
import org.bson.UuidRepresentation;

import java.util.logging.Level;
import java.util.logging.Logger;

@Getter
@RequiredArgsConstructor
public class MongoConnector {

    private final GroupStatsPlugin plugin;
    private final YamlDocument config;

    private MongoClient client;
    private MongoDatabase database;

    private MongoCollection<Document> profiles;

    public void init() {
        this.disableLogging();
        String connectionString = "";
        String database = "";

        if (config.getBoolean("MONGO.URI-MODE")) {
            connectionString = config.getString("MONGO.URI.CONNECTION_STRING");
            database = config.getString("MONGO.URI.DATABASE");
        } else {
            boolean auth = config.getBoolean("MONGO.NORMAL.AUTHENTICATION.ENABLED");
            String host = config.getString("MONGO.NORMAL.HOST");
            int port = config.getInt("MONGO.NORMAL.PORT");

            database = config.getString("MONGO.NORMAL.DATABASE");
            connectionString = "mongodb://" + host + ":" + port;

            if (auth) {
                String username = config.getString("MONGO.NORMAL.AUTHENTICATION.USERNAME");
                String password = config.getString("MONGO.NORMAL.AUTHENTICATION.PASSWORD");
                connectionString = "mongodb://" + username + ":" + password + "@" + host + ":" + port;
            }
        }

        this.client = MongoClients.create(
                MongoClientSettings.builder()
                        .uuidRepresentation(UuidRepresentation.STANDARD)
                        .applyConnectionString(new ConnectionString(connectionString))
                        .build()
        );

        this.database = client.getDatabase(database);
        this.database.createCollection("groupstats-profiles"); //precautionary
        this.profiles = this.database.getCollection("groupstats-profiles");

        plugin.getLogger().info("Initialized MongoDB successfully!");
    }

    public void shutdown() {
        plugin.getLogger().info("Disconnecting Mongo...");
        if (this.client != null) this.client.close();
        plugin.getLogger().info("Disconnected Mongo Successfully!");
    }

    public void disableLogging() {
        Logger mongoLogger = Logger.getLogger( "com.mongodb" );
        mongoLogger.setLevel(Level.SEVERE);

        Logger legacyLogger = Logger.getLogger( "org.mongodb" );
        legacyLogger.setLevel(Level.SEVERE);
    }
}