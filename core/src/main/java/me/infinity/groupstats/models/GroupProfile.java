package me.infinity.groupstats.models;

import com.google.gson.annotations.Expose;
import lombok.Data;
import me.infinity.groupstats.GroupNode;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Data
public class GroupProfile {

    private final UUID uniqueId;

    @Expose
    private Map<String, GroupNode> statistics;

    public GroupProfile(UUID uniqueId) {
        this.uniqueId = uniqueId;
        this.statistics = new ConcurrentHashMap<>();
    }
}
