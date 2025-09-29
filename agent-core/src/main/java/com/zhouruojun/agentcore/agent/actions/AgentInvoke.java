package com.zhouruojun.agentcore.agent.actions;

import com.alibaba.fastjson.JSONObject;
import com.zhouruojun.agentcore.agent.state.AgentMessageState;
import com.zhouruojun.agentcore.a2a.A2aClientManager;
import com.zhouruojun.agentcore.a2a.A2AInvocationResult;
import com.zhouruojun.agentcore.a2a.A2aTaskManager;
import com.zhouruojun.a2acore.spec.*;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElementHelper;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Agent调用节点
 * 负责调用指定的智能体并处理返回结果
 */
@Slf4j
public class AgentInvoke implements NodeAction<AgentMessageState> {

    private final A2aClientManager a2aClientManager;
    private final List<JSONObject> jsonTools;

    public AgentInvoke(A2aClientManager a2aClientManager, List<ToolSpecification> toolSpecifications) {
        this.a2aClientManager = a2aClientManager;
        this.jsonTools = toolSpecifications.stream().map(tool -> {
            JSONObject messageTool = new JSONObject();
            messageTool.put("name", tool.name());
            messageTool.put("description", tool.description());
            JsonObjectSchema parameters = tool.parameters();
            Map<String, Object> parametersMap = JsonSchemaElementHelper.toMap(parameters);
            messageTool.put("parameters", parametersMap);
            return messageTool;
        }).collect(Collectors.toList());
    }

    @Override
    public Map<String, Object> apply(AgentMessageState state) {
        log.info("AgentInvoke executing");

        var messages = state.messages();
        if (messages.isEmpty()) {
            throw new IllegalArgumentException("no input provided!");
        }

        if (state.lastMessage().isEmpty()) {
            throw new IllegalArgumentException("no last message provided!");
        }

        Map<String, Object> data = state.data();
        Optional<String> nextAgent = state.nextAgent();
        Optional<String> username = state.username();
        Optional<String> sessionId = state.sessionId();

        if (nextAgent.isEmpty()) {
            log.error("nextAgent is null, data:{}", JSONObject.toJSONString(data));
            return Map.of("next", "userInput", "agent_response", "nextAgent值为空");
        }

        if (username.isEmpty()) {
            log.error("username is null, data:{}", JSONObject.toJSONString(data));
            return Map.of("next", "userInput", "agent_response", "username值为空");
        }

        if (sessionId.isEmpty()) {
            log.error("sessionId is null, data:{}", JSONObject.toJSONString(data));
            return Map.of("next", "userInput", "agent_response", "sessionId值为空");
        }

        String agentName = nextAgent.get();
        // String session = sessionId.get();

        String taskInstruction = getTaskInstruction(state);
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("message", taskInstruction);
        parameters.put("userTools", JSONObject.toJSONString(jsonTools));
        parameters.put("originalMessage", extractMessageContent(state.lastMessage().get()));

        log.info("Invoking agent {} with task instruction: {}", agentName, taskInstruction);
        
        // 检查A2A客户端管理器状态
        if (a2aClientManager == null) {
            log.error("A2A客户端管理器不可用");
            return Map.of(
                "next", "userInput",
                "agent_response", "A2A客户端管理器不可用",
                "username", username.get()
            );
        }
        
        // 检查Agent注册状态
        try {
            Map<String, Object> registrationStatus = a2aClientManager.getAgentRegistrationStatus(agentName);
            log.info("Agent registration status for {}: {}", agentName, registrationStatus);
        } catch (Exception e) {
            log.warn("Failed to get registration status: {}", e.getMessage());
        }
        
        // 使用A2A客户端管理器进行调用
        A2AInvocationResult invocationResult = invokeAgentWithA2A(agentName, taskInstruction, sessionId.get());

        if (!invocationResult.isSuccess()) {
            String errorMessage = invocationResult.getError() != null ? invocationResult.getError() : "智能体服务不可用";
            log.error("Agent {} invocation failed: {}", agentName, errorMessage);
            return Map.of(
                "next", "userInput",
                "agent_response", "调用智能体失败: " + errorMessage,
                "username", username.get()
            );
        }

        String filteredResponse = invocationResult.getResponse();
        Map<String, Object> result = Map.of(
                "next", "supervisor",
                "nextAgent", "",
                "currentAgent", agentName,
                "agent_response", filteredResponse,
                "processingStage", "final_reasoning"
        );

        log.info("AgentInvoke returning result: {}", result);
        return result;
    }

    /**
     * 使用A2A协议调用智能体
     */
    private A2AInvocationResult invokeAgentWithA2A(String agentName, String taskInstruction, String sessionId) {
        try {
            // 首先验证Agent是否可用
            log.info("Checking if agent {} is available...", agentName);
            boolean isHealthy = a2aClientManager.checkAgentHealth(agentName);
            if (!isHealthy) {
                log.warn("Agent {} is not healthy, attempting to register...", agentName);
            }
            
            // 使用A2A客户端管理器进行实际的A2A调用
            // A2AClient a2aClient = a2aClientManager.getA2aClient(agentName);
            
            // 构建A2A消息
            TextPart textPart = new TextPart();
            textPart.setText(taskInstruction);
            
            Message message = Message.builder()
                .role(Role.USER)
                .parts(Arrays.asList(textPart))
                .metadata(Map.of(
                    "method", "data_analysis",
                    "sessionId", sessionId,
                    "userEmail", "agent-core@system.com"
                ))
                .build();
            
            // 构建任务参数
            TaskSendParams taskSendParams = TaskSendParams.builder()
                .id("task-" + System.currentTimeMillis())
                .sessionId(sessionId)
                .message(message)
                .acceptedOutputModes(Arrays.asList("text", "data"))
                .metadata(Map.of(
                    "method", "data_analysis",
                    "userEmail", "agent-core@system.com",
                    "userTools", "[]"
                ))
                .build();
            
            // 发送流式任务订阅并等待响应
            String response = a2aClientManager.sendTaskSubscribeAndWait(agentName, taskSendParams, sessionId);
            
            // 保存任务参数用于后续处理
            A2aTaskManager.getInstance().saveTaskParams(taskSendParams);
            
            return A2AInvocationResult.success(response, taskSendParams.getId(), sessionId);
            
        } catch (Exception e) {
            log.error("A2A调用失败", e);
            return A2AInvocationResult.failure("A2A调用异常: " + e.getMessage());
        }
    }

    private String getTaskInstruction(AgentMessageState state) {
        Optional<String> taskInstruction = state.value("taskInstruction");
        if (taskInstruction.isPresent() && !taskInstruction.get().trim().isEmpty()) {
            log.info("Using SupervisorAgent inferred task instruction: {}", taskInstruction.get());
            return taskInstruction.get();
        }

        ChatMessage lastMessage = state.lastMessage().get();
        String messageContent = extractMessageContent(lastMessage);
        log.info("Using last message as task instruction: {}", messageContent);
        return messageContent;
    }

    private String extractMessageContent(ChatMessage message) {
        if (message instanceof UserMessage userMessage) {
            return userMessage.singleText();
        } else if (message instanceof AiMessage aiMessage) {
            return aiMessage.text();
        } else if (message instanceof ToolExecutionResultMessage toolMessage) {
            return "Tool execution result: " + toolMessage.text();
        } else {
            return message.toString();
        }
    }

}