package me.infinity.groupstats.manager;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import org.bson.conversions.Bson; // Ensure Bson is imported if not already
import org.bson.conversions.Bson;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Handles direct data access operations with MongoDB for {@link me.infinity.groupstats.models.GroupProfile}
 * or other specified types.
 * All MongoDB operations are performed asynchronously, returning {@link CompletableFuture}
 * to prevent blocking the main server thread.
 * This class is generic but is primarily used with player profile data.
 *
 * @param <V> The type of the value object being stored/retrieved (e.g., GroupProfile).
 */
@SuppressWarnings("unused")
public class MongoStorage<V> {

    // Default ReplaceOptions for replace operations, enabling upsert.
    private static final ReplaceOptions REPLACE_OPTIONS = new ReplaceOptions().upsert(true);

    private final MongoCollection<Document> collection;
    private final Gson gson;
    private final Type typeToken;

    public MongoStorage(MongoCollection<Document> collection, Gson gson) {
        this.collection = collection;
        this.gson = gson;
        this.typeToken = new TypeToken<V>() {}.getType(); // TypeToken for generic deserialization.
    }

    /**
     * Asynchronously fetches all entries from the collection and deserializes them to type V.
     *
     * @return A CompletableFuture containing a List of all entries as type V.
     */
    public CompletableFuture<List<V>> fetchAllEntries() {
        return CompletableFuture.supplyAsync(() -> {
            List<V> found = new ArrayList<>();
            for (Document document : this.collection.find()) {
                if (document == null) {
                    continue;
                }
                found.add(this.gson.fromJson(document.toJson(), typeToken));
            }
            return found;
        }, CompletableFuture.delayedExecutor(1, java.util.concurrent.TimeUnit.MILLISECONDS)); // Small delay to ensure off-main-thread execution if pool is busy
    }

    /**
     * Asynchronously fetches all raw BSON Document entries from the collection.
     *
     * @return A CompletableFuture containing a List of all raw Documents.
     */
    public CompletableFuture<List<Document>> fetchAllRawEntries() {
        return CompletableFuture.supplyAsync(() -> {
            List<Document> found = new ArrayList<>();
            for (Document document : this.collection.find()) {
                found.add(document);
            }
            return found;
        }, CompletableFuture.delayedExecutor(1, java.util.concurrent.TimeUnit.MILLISECONDS));
    }

    /**
     * Asynchronously saves a data object (value) associated with a UUID key.
     * The entire document identified by the key is replaced (or created if it doesn't exist).
     * Note: For partial updates, especially in a concurrent environment, consider specific update methods.
     * This method is deprecated in favor of more specific update methods like {@link #updateSpecificGroupData}
     * or methods that perform atomic operations if appropriate, to avoid unintended data overwrites in the new Redis-based system.
     *
     * @param key   The UUID key.
     * @param value The value object to save.
     * @param type  The {@link Type} of the value, for Gson serialization.
     */
    @Deprecated
    public void saveData(UUID key, V value, Type type) {
        CompletableFuture.runAsync(() -> this.saveDataSync(key, value, type), CompletableFuture.delayedExecutor(1, java.util.concurrent.TimeUnit.MILLISECONDS));
    }

    /**
     * Synchronously saves a data object. Called by the asynchronous {@link #saveData(UUID, Object, Type)}.
     * Marked as deprecated along with its async counterpart.
     *
     * @param key   The UUID key.
     * @param value The value object.
     * @param type  The Type for Gson.
     */
    @Deprecated
    public void saveDataSync(UUID key, V value, Type type) {
        Bson query = Filters.eq("_id", key.toString());
        Document parsed = Document.parse(gson.toJson(value, type));
        this.collection.replaceOne(query, parsed, REPLACE_OPTIONS);
    }

    /**
     * Asynchronously saves a raw BSON Document.
     * The entire document identified by the key is replaced (or created if it doesn't exist).
     * Similar deprecation caution as {@link #saveData(UUID, Object, Type)} if used for player profiles.
     *
     * @param key      The UUID key.
     * @param document The Document to save.
     */
    @Deprecated
    public void saveRawData(UUID key, Document document) {
        CompletableFuture.runAsync(() -> this.saveRawDataSync(key, document), CompletableFuture.delayedExecutor(1, java.util.concurrent.TimeUnit.MILLISECONDS));
    }

    /**
     * Synchronously saves a raw BSON Document.
     * Marked as deprecated along with its async counterpart.
     */
    @Deprecated
    public void saveRawDataSync(UUID key, Document document) {
        Bson query = Filters.eq("_id", key.toString());
        this.collection.replaceOne(query, document, REPLACE_OPTIONS);
    }

    /**
     * Synchronously loads data for a given UUID key and deserializes it to the specified type.
     * Note: This is a synchronous call. Prefer {@link #loadDataAsync(UUID, Type)} for non-blocking operations.
     *
     * @param key  The UUID key.
     * @param type The {@link Type} to deserialize to.
     * @return The deserialized object, or null if not found.
     */
    public V loadData(UUID key, Type type) {
        Bson query = Filters.eq("_id", key.toString());

        Document document = this.collection.find(query).first();
        if (document == null) return null;

        return this.gson.fromJson(document.toJson(), type);
    }

    /**
     * Asynchronously loads data for a given UUID key.
     *
     * @param key  The UUID key.
     * @param type The {@link Type} to deserialize to.
     * @return A CompletableFuture containing the deserialized object, or null if not found.
     */
    public CompletableFuture<V> loadDataAsync(UUID key, Type type) {
        return CompletableFuture.supplyAsync(() -> this.loadData(key, type), CompletableFuture.delayedExecutor(1, java.util.concurrent.TimeUnit.MILLISECONDS));
    }

    /**
     * Synchronously loads a raw BSON Document for a given UUID key.
     * Prefer {@link #loadRawDataAsync(UUID)} for non-blocking operations.
     * @param key The UUID key.
     * @return The Document, or null if not found.
     */
    public Document loadRawData(UUID key) {
        Bson query = Filters.eq("_id", key.toString());
        return this.collection.find(query).first();
    }

    /**
     * Asynchronously loads a raw BSON Document for a given UUID key.
     *
     * @param key The UUID key.
     * @return A CompletableFuture containing the Document, or null if not found.
     */
    public CompletableFuture<Document> loadRawDataAsync(UUID key) {
        return CompletableFuture.supplyAsync(() -> this.loadRawData(key), CompletableFuture.delayedExecutor(1, java.util.concurrent.TimeUnit.MILLISECONDS));
    }

    /**
     * Asynchronously deletes data associated with a UUID key.
     *
     * @param key The UUID key of the document to delete.
     */
    public void deleteData(UUID key) {
        CompletableFuture.runAsync(() -> {
            Bson query = Filters.eq("_id", key.toString());
            this.collection.deleteOne(query);
        }, CompletableFuture.delayedExecutor(1, java.util.concurrent.TimeUnit.MILLISECONDS));
    }

    /**
     * Asynchronously removes a specific top-level key from all documents in the collection.
     *
     * @param key The top-level key to remove (unset).
     * @return A CompletableFuture containing the number of documents modified.
     */
    public CompletableFuture<Long> deleteKeyInAll(String key) {
        return CompletableFuture.supplyAsync(() -> {
            // Unset the key
            Bson combinedUpdate = Updates.unset(key);

            // Apply the updates to all documents in the collection
            return collection.updateMany(new Document(), combinedUpdate).getModifiedCount();  // new Document() is an empty filter, meaning "all documents";
        }, CompletableFuture.delayedExecutor(1, java.util.concurrent.TimeUnit.MILLISECONDS));
    }

    /**
     * Asynchronously updates or sets a specific group's data within a player's document in MongoDB.
     * This method uses the $set operator to modify only the specified group's sub-document or fields,
     * typically structured under a "groups" parent field (e.g., "groups.SOLO", "groups.DUOS").
     * The keys within the {@code groupData} map are expected to be the short stat keys
     * defined in {@link me.infinity.groupstats.models.StatKeys}.
     * If the document or the specified path doesn't exist, they will be created due to upsert.
     *
     * @param playerUUID The UUID of the player whose document is to be updated.
     * @param groupKey   The key for the specific group (e.g., "SOLO", "DUOS"). This will be a sub-field under a "groups" field.
     * @param groupData  A Map representing the data for the group, keyed by short stat keys (from {@link me.infinity.groupstats.models.StatKeys}).
     *                   Example: {"k": 10, "w": 5}
     * @return A CompletableFuture that completes when the operation is done.
     */
    public CompletableFuture<Void> updateSpecificGroupData(UUID playerUUID, String groupKey, java.util.Map<String, Object> groupData) {
        return CompletableFuture.runAsync(() -> {
            Bson query = Filters.eq("_id", playerUUID.toString());
            Document groupDocument = new Document(groupData); // Convert Map to BSON Document for $set

            // Construct the update to set the specific group's data, e.g., "groups.SOLO" = {kills: 10, wins: 1}
            String fieldPath = "groups." + groupKey.toUpperCase();
            Bson update = Updates.set(fieldPath, groupDocument);

            // Use upsert option to create the document and/or fields if they don't exist.
            UpdateOptions options = new UpdateOptions().upsert(true);

            collection.updateOne(query, update, options);
        }, CompletableFuture.delayedExecutor(1, java.util.concurrent.TimeUnit.MILLISECONDS));
    }

    /**
     * Asynchronously and atomically increments numerical stats for a specific group within a player's document.
     * This method uses MongoDB's $inc operator for each stat provided.
     * The stats are updated under the "groups.{groupKey}.{shortStatKey}" path.
     * Keys in the {@code statsToIncrement} map must be short stat keys from {@link me.infinity.groupstats.models.StatKeys}.
     * If the document or path doesn't exist, upsert will create them.
     *
     * @param playerUUID       The UUID of the player.
     * @param groupKey         The group key (e.g., "SOLO", "DUOS"). This will be part of the path.
     * @param statsToIncrement A map where keys are short stat keys (from {@link me.infinity.groupstats.models.StatKeys})
     *                         (e.g., "k", "w") and values are the numerical amounts to increment by.
     * @return A {@link CompletableFuture} that completes when the MongoDB operation is finished.
     */
    public CompletableFuture<Void> incrementGroupStats(UUID playerUUID, String groupKey, java.util.Map<String, Number> statsToIncrement) {
        return CompletableFuture.runAsync(() -> {
            if (statsToIncrement == null || statsToIncrement.isEmpty()) {
                // No stats to increment, operation is a no-op.
                return;
            }

            Bson query = Filters.eq("_id", playerUUID.toString());
            List<Bson> individualUpdates = new ArrayList<>();

            // Create an $inc operation for each stat in the map
            for (java.util.Map.Entry<String, Number> entry : statsToIncrement.entrySet()) {
                String statFieldPath = "groups." + groupKey.toUpperCase() + "." + entry.getKey();
                individualUpdates.add(Updates.inc(statFieldPath, entry.getValue()));
            }

            if (individualUpdates.isEmpty()) {
                // Should not happen if statsToIncrement was not empty, but as a safeguard.
                return;
            }

            // Combine all $inc operations into a single update command
            Bson combinedUpdate = Updates.combine(individualUpdates);
            UpdateOptions options = new UpdateOptions().upsert(true); // Create document/fields if they don't exist

            collection.updateOne(query, combinedUpdate, options);
        }, CompletableFuture.delayedExecutor(1, java.util.concurrent.TimeUnit.MILLISECONDS));
    }
}