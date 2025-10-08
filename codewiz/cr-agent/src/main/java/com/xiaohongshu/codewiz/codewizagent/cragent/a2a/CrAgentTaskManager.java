package com.xiaohongshu.codewiz.codewizagent.cragent.a2a;

import cn.hutool.core.collection.CollectionUtil;
import com.alibaba.fastjson.JSONObject;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.xiaohongshu.codewiz.a2acore.core.InMemoryTaskManager;
import com.xiaohongshu.codewiz.a2acore.core.PushNotificationSenderAuth;
import com.xiaohongshu.codewiz.a2acore.spec.Artifact;
import com.xiaohongshu.codewiz.a2acore.spec.DataPart;
import com.xiaohongshu.codewiz.a2acore.spec.Message;
import com.xiaohongshu.codewiz.a2acore.spec.Part;
import com.xiaohongshu.codewiz.a2acore.spec.Role;
import com.xiaohongshu.codewiz.a2acore.spec.Task;
import com.xiaohongshu.codewiz.a2acore.spec.TaskArtifactUpdateEvent;
import com.xiaohongshu.codewiz.a2acore.spec.TaskIdParams;
import com.xiaohongshu.codewiz.a2acore.spec.TaskSendParams;
import com.xiaohongshu.codewiz.a2acore.spec.TaskState;
import com.xiaohongshu.codewiz.a2acore.spec.TaskStatus;
import com.xiaohongshu.codewiz.a2acore.spec.TaskStatusUpdateEvent;
import com.xiaohongshu.codewiz.a2acore.spec.TextPart;
import com.xiaohongshu.codewiz.a2acore.spec.UpdateEvent;
import com.xiaohongshu.codewiz.a2acore.spec.ValueError;
import com.xiaohongshu.codewiz.a2acore.spec.error.AuthenticationRequiredError;
import com.xiaohongshu.codewiz.a2acore.spec.error.InternalError;
import com.xiaohongshu.codewiz.a2acore.spec.error.InvalidParamsError;
import com.xiaohongshu.codewiz.a2acore.spec.error.MethodNotFoundError;
import com.xiaohongshu.codewiz.a2acore.spec.message.JsonRpcRequest;
import com.xiaohongshu.codewiz.a2acore.spec.message.JsonRpcResponse;
import com.xiaohongshu.codewiz.a2acore.spec.message.SendTaskRequest;
import com.xiaohongshu.codewiz.a2acore.spec.message.SendTaskResponse;
import com.xiaohongshu.codewiz.a2acore.spec.message.SendTaskStreamingRequest;
import com.xiaohongshu.codewiz.a2acore.spec.message.SendTaskStreamingResponse;
import com.xiaohongshu.codewiz.a2acore.spec.message.TaskResubscriptionRequest;
import com.xiaohongshu.codewiz.a2acore.spec.message.util.Util;
import com.xiaohongshu.codewiz.codewizagent.cragent.agent.AgentChatRequest;
import com.xiaohongshu.codewiz.codewizagent.cragent.agent.core.AgentControllerCore;
import com.xiaohongshu.codewiz.codewizagent.cragent.agent.state.AgentMessageState;
import com.xiaohongshu.codewiz.codewizagent.cragent.mcp.ToolSpecificationHelper;
import com.xiaohongshu.codewiz.codewizagent.cragent.mcp.local.LocalAgentReplyMessage;
import com.xiaohongshu.codewiz.codewizagent.cragent.mcp.local.LocalToolMessageRequest;
import com.xiaohongshu.codewiz.codewizagent.cragent.mcp.local.ToolInterceptorRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.bsc.async.AsyncGenerator;
import org.bsc.langgraph4j.NodeOutput;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * <p>
 * crAgent的任务管理器
 * </p>
 *
 * @author 瑞诺
 * create on 2025/5/12 11:45
 */
@Slf4j
@Component
public class CrAgentTaskManager extends InMemoryTaskManager {

    private static CrAgentTaskManager INSTANCE;

    @Resource
    private AgentControllerCore agentControllerCore;

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private PushNotificationSenderAuth pushNotificationSenderAuth;

    @PostConstruct
    public void init() {
        INSTANCE = this;
        super.pushNotificationSenderAuth = pushNotificationSenderAuth;
    }

    private final List<String> supportModes = Arrays.asList("text", "file", "data");

    @Override
    public Mono<SendTaskResponse> onSendTask(SendTaskRequest request) {
        log.info("onSendTask not support, request: {}", request);
        return null;
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
            log.info("onSendTaskSubscribe request: {}", request);
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

            Object userToolsObj = metadata.getOrDefault("userTools", "[]");
            List<ToolSpecification> userTools = getUserTools((String) userToolsObj);

            Task tempTask = tasks.get(ps.getId());

            AsyncGenerator<NodeOutput<AgentMessageState>> messages;
            if (tempTask == null) {
                // save
                createOrUpdateTask(ps);
                // 返回一个任务被提交的sse响应
                sendTaskUpdateEvent(ps.getId(), new TaskStatus(TaskState.SUBMITTED), false);
                agentControllerCore.processStreamWithA2a(ps.getId(), ps.getSessionId(), ps.getMessage(), userTools, userEmail);
            } else {
                TaskStatus status = tempTask.getStatus();
                // 暂时禁用当前任务的状态检查，message时序还没调整好
//                if (checkFinalState(status.getState())) {
//                    throw new ValueError("Task is already completed");
//                } else if (checkWorkingState(status.getState())) {
//                    throw new ValueError("Task is already working");
//                } else
                if (checkSubmitState(status.getState())) {
                    throw new ValueError("Task is already submitted");
                } else {
                    // 只有暂定状态的，才允许继续执行
                    createOrUpdateTask(ps);
                    Map<String, Object> taskSendParamsMetaData = ps.getMetadata();
                    Object method = taskSendParamsMetaData.get("method");
                    List<Part> parts = ps.getMessage().getParts();
                    switch ((String)method) {
                        case "tool_call":
                            // 处理工具调用
                            // 有且只有一个part，并且是DataPart
                            if (parts.getLast() != null && parts.getLast() instanceof DataPart dataPart) {
                                Object toolCall = dataPart.getData().get("data");
                                agentControllerCore.resumeStreamWithTools((LocalAgentReplyMessage) toolCall, userEmail);
                            } else {
                                throw new ValueError("Invalid tool call");
                            }
                            break;
                        case "user_input":
                            // 处理用户输入
                            // 只有一个part，并且是TextPard
                            if (parts.getFirst() != null && parts.getFirst() instanceof TextPart textPart) {
                                String userInput = textPart.getText();
                                AgentChatRequest agentChatRequest = new AgentChatRequest();
                                agentChatRequest.setRequestId(id);
                                agentChatRequest.setChat(userInput);
                                agentControllerCore.humanInput(agentChatRequest);
                            } else {
                                throw new ValueError("Invalid user input");
                            }
                            break;
                        case "human_confirm":
                            if (parts.getFirst() != null && parts.getFirst() instanceof DataPart dataPart) {
                                Object toolInterceptorRequest = dataPart.getData().get("data");
                                agentControllerCore.humanConfirm((ToolInterceptorRequest) toolInterceptorRequest);
                            } else {
                                throw new ValueError("Invalid human confirm");
                            }
                            break;
                        case "human_replay":
                            if (parts.getFirst() != null && parts.getFirst() instanceof DataPart dataPart) {
                                Object toolInterceptorRequest = dataPart.getData().get("data");
                                agentControllerCore.humanReplay((ToolInterceptorRequest) toolInterceptorRequest);
                            } else {
                                throw new ValueError("Invalid human replay");
                            }
                            break;
                        default:
                            throw new UnsupportedOperationException("Unsupported method: " + method);
                    }
                }
            }
//            return new SendTaskStreamingResponse(id, new TaskStatusUpdateEvent(id, new TaskStatus(TaskState.SUBMITTED), false));
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
            return Mono.just(new JsonRpcResponse<>(request.getId(), new InternalError("An error occurred while reconnecting to stream: " + e.getMessage())));
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

    public Boolean checkFinalState(TaskState taskState) {
        return TaskState.COMPLETED.equals(taskState)
                || TaskState.FAILED.equals(taskState)
                || TaskState.CANCELED.equals(taskState)
                || TaskState.UNKNOWN.equals(taskState)
                ;
    }

    public Boolean checkWorkingState(TaskState taskState) {
        return TaskState.WORKING.equals(taskState);
    }

    public Boolean checkPauseState(TaskState taskState) {
        return TaskState.INPUT_REQUIRED.equals(taskState);
    }

    public Boolean checkSubmitState(TaskState taskState) {
        return TaskState.SUBMITTED.equals(taskState);
    }

    private List<ToolSpecification> getUserTools(String userToolsObj) {
        try {
            ArrayNode arrayNode = (ArrayNode) objectMapper.readTree((String)userToolsObj);
            return ToolSpecificationHelper.toolSpecificationListFromMcpResponse(arrayNode);
        } catch (Exception e) {
            log.error("getUserTools error", e);
            return Collections.emptyList();
        }
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
        Task latestTask = this.updateStore(taskId, taskStatus, Collections.singletonList(artifact));
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

    public static void sendTextEvent(String requestId, AgentMessageState state, String node) {
        TextPart textPart = new TextPart(state.lastMessage().toString());
        Message messageBuild = Message.builder()
                .role(Role.AGENT)
                .parts(Collections.singletonList(textPart))
                .metadata(Map.of(
                        "requestId", requestId,
                        "method", node
                ))
                .build();
        TaskStatus taskStatus = new TaskStatus(TaskState.WORKING, messageBuild);
        CrAgentTaskManager.sendTaskUpdateEvent(requestId, taskStatus, "__END__".equals(node));
    }

    public static void sendDataEvent(String requestId, LocalToolMessageRequest localToolMessageRequest, String node) {
        DataPart dataPart = new DataPart();
        dataPart.setData(Map.of(
                "requestId",localToolMessageRequest.getRequestId(),
                "sessionId", localToolMessageRequest.getSessionId(),
                "tools", localToolMessageRequest.getTools()));
        Message messageBuild = Message.builder()
                .role(Role.AGENT)
                .parts(Collections.singletonList(dataPart))
                .metadata(Map.of(
                        "requestId", requestId,
                        "sessionId", localToolMessageRequest.getSessionId(),
                        "method", node
                ))
                .build();
        TaskStatus taskStatus = new TaskStatus(TaskState.INPUT_REQUIRED, messageBuild);
        CrAgentTaskManager.sendTaskUpdateEvent(requestId, taskStatus, "__END__".equals(node));
    }

    public static void sendTaskUpdateEvent(String taskId,  TaskStatus taskStatus, boolean finalFlag) {
        CrAgentTaskManager.INSTANCE.updateTaskAndSendEventAndNotification(taskId, taskStatus, null, finalFlag);
    }

    public static boolean areModalitiesCompatible(List<String> serverOutputModes, List<String> clientOutputModes) {
        if (serverOutputModes == null || serverOutputModes.isEmpty()) {
            return true;
        }
        if (clientOutputModes == null || clientOutputModes.isEmpty()) {
            return true;
        }
        return CollectionUtil.containsAll(clientOutputModes, serverOutputModes);
    }
}
