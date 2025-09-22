package com.zhouruojun.agentcore.core;

import com.zhouruojun.agentcore.spec.Task;

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
