package com.zhouruojun.a2acore.core;

import com.zhouruojun.a2acore.spec.Task;
import java.util.HashMap;
import java.util.Map;


public class InMemoryTaskOperationManager implements TaskOperationManager {

    final Map<String, Task> tasksMap = new HashMap<>();


    @Override
    public Task get(String id) {
        return tasksMap.get(id);
    }

    @Override
    public void put(String id, Task task) {
        tasksMap.put(id, task);
    }

    @Override
    public void remove(String id) {
        tasksMap.remove(id);
    }

}


