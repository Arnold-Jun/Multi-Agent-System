package com.zhouruojun.a2acore.core;

import com.zhouruojun.a2acore.spec.Task;

/**
 * <p>
 *
 * </p>
 *
 */
public interface TaskOperationManager {

    Task get(String id);

    void put(String id, Task task);

    void remove(String id);
}


