package me.infinity.groupstats.task;

import lombok.RequiredArgsConstructor;
import me.infinity.groupstats.manager.GroupManager;

@RequiredArgsConstructor
public class GroupUpdateTask implements Runnable {

    private final GroupManager groupManager;

    @Override
    public void run() {
        groupManager.getCache().values().forEach(groupManager::save);
    }
}
