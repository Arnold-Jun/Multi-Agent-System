package com.zhouruojun.dataanalysisagent.a2a;

import cn.hutool.core.collection.CollectionUtil;
import com.alibaba.fastjson.JSONObject;
import com.zhouruojun.a2acore.core.InMemoryTaskManager;
import com.zhouruojun.a2acore.core.PushNotificationSenderAuth;
import com.zhouruojun.a2acore.spec.*;
import com.zhouruojun.a2acore.spec.error.*;
import com.zhouruojun.a2acore.spec.message.*;
import com.zhouruojun.a2acore.spec.message.util.Util;
import com.zhouruojun.dataanalysisagent.agent.core.DataAnalysisControllerCore;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import jakarta.annotation.PostConstruct;
import java.util.*;

/**
 * 数据分析任务管理器 - 基于codewiz的CrAgentTaskManager经验重构
 * 作为A2A协议的入口，处理流式任务和多种交互方法
 */
@Slf4j
@Component
public class DataAnalysisTaskManager extends InMemoryTaskManager {

    private static DataAnalysisTaskManager INSTANCE;

    @Autowired
    private DataAnalysisControllerCore dataAnalysisControllerCore;


    @Autowired
    private PushNotificationSenderAuth pushNotificationSenderAuth;

    @PostConstruct
    public void init() {
        INSTANCE = this;
        super.pushNotificationSenderAuth = pushNotificationSenderAuth;
    }

    private final List<String> supportModes = Arrays.asList("text", "data");
    
    @Override
    public Mono<SendTaskResponse> onSendTask(SendTaskRequest request) {
        SendTaskResponse errorResponse = new SendTaskResponse(request.getId(), 
            new com.zhouruojun.a2acore.spec.error.InternalError("onSendTask method not supported, use onSendTaskSubscribe instead"));
        return Mono.just(errorResponse);
    }

    @Override
    public Mono<? extends JsonRpcResponse<?>> onSendTaskSubscribe(SendTaskStreamingRequest request) {
        log.info("onSendTaskSubscribe request: {}", JSONObject.toJSONString(request));
        TaskSendParams params = request.getParams();
        String id = params.getId();
        Map<String, Object> metadata = params.getMetadata();
        String userEmail = (String) metadata.getOrDefault("userEmail", "");
        if (StringUtils.isEmpty(userEmail)) {
            return Mono.just(new SendTaskStreamingResponse(id, new AuthenticationRequiredError()));
        }

        return Mono.fromRunnable(() -> {
            TaskSendParams ps = request.getParams();
            // 1. check
            JsonRpcResponse<Object> error = this.validRequest(request);
            if (error != null) {
                throw new ValueError(error.getError().getMessage());
            }
            // check id and skill
            if (StringUtils.isEmpty(ps.getId())) {
                throw new ValueError("Task ID is missing");
            }

            Task tempTask = this.taskOperationManager.get(ps.getId());

            if (tempTask == null) {
                // 新任务：save 并创建任务
                createOrUpdateTask(ps);
                sendTaskUpdateEvent(ps.getId(), new TaskStatus(TaskState.SUBMITTED), false);
            } else {
                // 已存在任务：检查状态
                TaskStatus status = tempTask.getStatus();
                if (checkSubmitState(status.getState())) {
                    throw new ValueError("Task is already submitted");
                } else {
                    // 只有暂定状态的，才允许继续执行
                    createOrUpdateTask(ps);
                }
            }
            
            // 统一处理任务 - A2A消息只包含TextPart
            List<Part> parts = ps.getMessage().getParts();
            if (parts.isEmpty()) {
                throw new ValueError("Message parts is empty");
            }
            
            Part firstPart = parts.getFirst();
            if (!(firstPart instanceof TextPart)) {
                throw new ValueError("A2A协议消息只支持TextPart，不支持: " + firstPart.getClass().getSimpleName());
            }
            
            String taskInstruction = ((TextPart) firstPart).getText();
            
            // 统一调用A2A同步处理
            String result = dataAnalysisControllerCore.processA2aSync(ps.getSessionId(), taskInstruction);
            sendTextEvent(ps.getId(), result, "agent_response");
        });
    }
    
    /**
     * 这里是重新订阅了当前任务的请求
     * 由于当前实现是由内存，以及内存队列实现。重新订阅是从最新的消息开始。
     * 后续改为持久化的状态，是可以拉取所有历史信息的。
     * @param request
     * @return
     */
    @Override
    public Mono<? extends JsonRpcResponse<?>> onResubscribeTask(TaskResubscriptionRequest request) {
        TaskIdParams params = request.getParams();
        try {
            this.initEventQueue(params.getId(), true);
        } catch (Exception e) {
            log.error("Error while reconnecting to SSE stream: {}", e.getMessage());
            return Mono.just(new JsonRpcResponse<>(request.getId(), new com.zhouruojun.a2acore.spec.error.InternalError("An error occurred while reconnecting to stream: " + e.getMessage())));
        }

        return Mono.empty();
    }

    private JsonRpcResponse<Object> validRequest(JsonRpcRequest<TaskSendParams> request) {
        TaskSendParams ps = request.getParams();
        if (!areModalitiesCompatible(supportModes, ps.getAcceptedOutputModes())) {
            log.warn("Unsupported output mode. Received: {}, Support: {}",
                    ps.getAcceptedOutputModes(),
                    supportModes);
            return Util.newIncompatibleTypesError(request.getId());
        }

        if (ps.getPushNotification() != null && Util.isEmpty(ps.getPushNotification().getUrl())) {
            log.warn("Push notification URL is missing");
            return new JsonRpcResponse<>(request.getId(), new InvalidParamsError("Push notification URL is missing"));
        }
        return null;
    }


    public Boolean checkSubmitState(TaskState taskState) {
        return TaskState.SUBMITTED.equals(taskState);
    }

    
    /**
     * 更新并发送通知
     * sse:
     *  发送制品的更新通知
     *  发送状态的更新通知
     * webhook:
     *  发送所有的更新通知
     */
    private void updateTaskAndSendEventAndNotification(String taskId, TaskStatus taskStatus, Artifact artifact, boolean finalFlag) {
        Task latestTask = this.updateStore(taskId, taskStatus, artifact != null ? Collections.singletonList(artifact) : null);
        // send notification
        this.sendTaskNotification(latestTask);

        // artifact event
        if (artifact != null) {
            TaskArtifactUpdateEvent taskArtifactUpdateEvent = new TaskArtifactUpdateEvent(taskId, artifact);
            if (taskArtifactUpdateEvent.getMetadata() == null) {
                taskArtifactUpdateEvent.setMetadata(new HashMap<>());
            }
            taskArtifactUpdateEvent.getMetadata().put("requestId", latestTask.getId());
            taskArtifactUpdateEvent.getMetadata().put("sessionId", latestTask.getSessionId());
            this.enqueueEvent(taskId, taskArtifactUpdateEvent);
        }

        // status event
        TaskStatusUpdateEvent taskStatusUpdateEvent = new TaskStatusUpdateEvent(taskId, taskStatus, finalFlag);
        if (taskStatusUpdateEvent.getMetadata() == null) {
            taskStatusUpdateEvent.setMetadata(new HashMap<>());
        }
        taskStatusUpdateEvent.getMetadata().put("requestId", latestTask.getId());
        taskStatusUpdateEvent.getMetadata().put("sessionId", latestTask.getSessionId());
        this.enqueueEvent(taskId, taskStatusUpdateEvent);
    }

    public void createOrUpdateTask(TaskSendParams ps) {
        // save
        this.upsertTask(ps);
        Task task = this.updateStore(ps.getId(), new TaskStatus(TaskState.WORKING), null);

        this.initEventQueue(task.getId(), false);
    }

    public static void sendTextEvent(String requestId, String result, String node) {
        TextPart textPart = new TextPart(result);
        Message messageBuild = Message.builder()
                .role(Role.AGENT)
                .parts(Collections.singletonList(textPart))
                .metadata(Map.of(
                        "requestId", requestId,
                        "method", node
                ))
                .build();
        TaskStatus taskStatus = new TaskStatus(TaskState.WORKING, messageBuild);
        DataAnalysisTaskManager.sendTaskUpdateEvent(requestId, taskStatus, "__END__".equals(node));
    }

    public static void sendTaskUpdateEvent(String taskId, TaskStatus taskStatus, boolean finalFlag) {
        DataAnalysisTaskManager.INSTANCE.updateTaskAndSendEventAndNotification(taskId, taskStatus, null, finalFlag);
    }

    public static boolean areModalitiesCompatible(List<String> serverOutputModes, List<String> clientOutputModes) {
        if (serverOutputModes == null || serverOutputModes.isEmpty()) {
            return true;
        }
        if (clientOutputModes == null || clientOutputModes.isEmpty()) {
            return true;
        }
        // 服务端必须支持客户端请求的所有输出模式
        return CollectionUtil.containsAll(serverOutputModes, clientOutputModes);
    }
}