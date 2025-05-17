package me.infinity.groupstats;

import com.google.gson.annotations.Expose;
import lombok.Data;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Data
public class GroupProfile {

    @Expose
    private final UUID uniqueId;

    @Expose
    private Map<String, GroupNode> statistics;

    public GroupProfile(UUID uniqueId) {
        this.uniqueId = uniqueId;
        this.statistics = new ConcurrentHashMap<>();
    }
}
