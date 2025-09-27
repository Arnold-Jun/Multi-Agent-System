package com.zhouruojun.agentcore.a2a;

import cn.hutool.core.collection.CollectionUtil;
import com.zhouruojun.agentcore.spec.TaskSendParams;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A2A任务管理器
 * 管理任务参数和会话映射关系
 *
 */
@Slf4j
public class A2aTaskManager {

    private static final A2aTaskManager INSTANCE = new A2aTaskManager();
    private final Map<String, TaskSendParams> taskSendParamsMap;
    private final Map<String, List<String>> taskWithSessionIdMap;

    private A2aTaskManager() {
        taskSendParamsMap = new ConcurrentHashMap<>();
        taskWithSessionIdMap = new ConcurrentHashMap<>();
    }

    public static A2aTaskManager getInstance() {
        return INSTANCE;
    }

    /**
     * 获取任务参数
     * @param taskId 任务ID
     * @return 任务参数
     */
    public TaskSendParams getTaskParams(String taskId) {
        return taskSendParamsMap.get(taskId);
    }

    /**
     * 获取会话的最后一个任务参数
     * @param sessionId 会话ID
     * @return 任务参数
     */
    public TaskSendParams lastTaskParams(String sessionId) {
        List<String> strings = taskWithSessionIdMap.get(sessionId);
        if (CollectionUtil.isNotEmpty(strings)) {
            return taskSendParamsMap.get(strings.getLast());
        }
        return null;
    }

    /**
     * 保存任务参数
     * @param taskSendParams 任务参数
     */
    public void saveTaskParams(TaskSendParams taskSendParams) {
        taskSendParamsMap.put(taskSendParams.getId(), taskSendParams);
        List<String> taskIdList = taskWithSessionIdMap.getOrDefault(taskSendParams.getSessionId(), new LinkedList<>());
        taskIdList.add(taskSendParams.getId());
        taskWithSessionIdMap.put(taskSendParams.getSessionId(), taskIdList);
    }

    /**
     * 根据会话清理任务
     * @param sessionId 会话ID，类比主agent的requestId
     */
    public void cleanTaskBySession(String sessionId) {
        List<String> strings = taskWithSessionIdMap.get(sessionId);
        if (CollectionUtil.isNotEmpty(strings)) {
            strings.forEach(taskId -> taskSendParamsMap.remove(taskId));
            taskWithSessionIdMap.remove(sessionId);
        }
    }

    /**
     * 移除任务参数
     * @param taskId 任务ID
     */
    public void removeTaskParams(String taskId) {
        taskSendParamsMap.remove(taskId);
    }
}




