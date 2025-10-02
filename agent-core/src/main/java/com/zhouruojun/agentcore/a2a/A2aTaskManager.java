package com.zhouruojun.agentcore.a2a;

import cn.hutool.core.collection.CollectionUtil;
import com.zhouruojun.a2acore.spec.TaskSendParams;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A2A任务管理器
 * 管理任务生命周期和会话状态
 */
@Slf4j
public class A2aTaskManager {

    private static final A2aTaskManager INSTANCE = new A2aTaskManager();
    private Map<String, TaskSendParams> taskSendParamsMap;
    private Map<String, List<String>> taskWithSessionIdMap;

    private A2aTaskManager() {
        taskSendParamsMap = new ConcurrentHashMap<>();
        taskWithSessionIdMap = new ConcurrentHashMap<>();
    }

    public static A2aTaskManager getInstance() {
        return INSTANCE;
    }

    public TaskSendParams getTaskParams(String taskId) {
        return taskSendParamsMap.get(taskId);
    }

    public TaskSendParams lastTaskParams(String sessionId) {
        List<String> strings = taskWithSessionIdMap.get(sessionId);
        if (CollectionUtil.isNotEmpty(strings)) {
            return taskSendParamsMap.get(strings.getLast());
        }
        return null;
    }

    public void saveTaskParams(TaskSendParams taskSendParams) {
        taskSendParamsMap.put(taskSendParams.getId(), taskSendParams);
        List<String> taskIdList = taskWithSessionIdMap.getOrDefault(taskSendParams.getSessionId(), new LinkedList<>());
        taskIdList.add(taskSendParams.getId());
        taskWithSessionIdMap.put(taskSendParams.getSessionId(), taskIdList);
    }

    /**
     * 清理会话相关的任务
     * @param sessionId 会话ID
     */
    public void cleanTaskBySession(String sessionId) {
        List<String> strings = taskWithSessionIdMap.get(sessionId);
        if (CollectionUtil.isNotEmpty(strings)) {
            strings.forEach(taskId -> taskSendParamsMap.remove(taskId));
            taskWithSessionIdMap.remove(sessionId);
        }
    }

    public void removeTaskParams(String taskId) {
        taskSendParamsMap.remove(taskId);
    }

    /**
     * 获取会话的所有任务ID
     */
    public List<String> getSessionTaskIds(String sessionId) {
        return taskWithSessionIdMap.getOrDefault(sessionId, new LinkedList<>());
    }

    /**
     * 获取活跃会话数量
     */
    public int getActiveSessionCount() {
        return taskWithSessionIdMap.size();
    }

    /**
     * 获取总任务数量
     */
    public int getTotalTaskCount() {
        return taskSendParamsMap.size();
    }
}
