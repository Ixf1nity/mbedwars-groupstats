package me.infinity.groupstats;

import lombok.RequiredArgsConstructor;
import me.infinity.groupstats.manager.GroupManager;

@RequiredArgsConstructor
public class GroupUpdateTask implements Runnable {

    private final GroupManager groupManager;

    @Override
    public void run() {
        groupManager.getCache().values().forEach(groupManager::save);
        if (groupManager.getInstance().getConfiguration().getBoolean("DEBUG")) {
            groupManager.getInstance().getLogger().info("Updating indexes, might cause lag. Who cares? Hussain is rich.");
        }
    }
}
