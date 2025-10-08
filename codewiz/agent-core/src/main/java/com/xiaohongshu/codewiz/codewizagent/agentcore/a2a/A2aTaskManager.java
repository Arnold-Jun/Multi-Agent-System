package com.xiaohongshu.codewiz.codewizagent.agentcore.a2a;

import cn.hutool.core.collection.CollectionUtil;
import com.xiaohongshu.codewiz.a2acore.spec.TaskSendParams;
import io.swagger.models.auth.In;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;

/**
 * <p>
 *
 * </p>
 *
 * @author 瑞诺
 * create on 2025/5/15 11:17
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
     *
     * @param sessionId 类比主agent的requestId
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
}
