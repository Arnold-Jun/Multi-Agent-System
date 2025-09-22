package com.zhouruojun.agentcore.agent.actions;

import com.alibaba.fastjson.JSONObject;
import com.zhouruojun.agentcore.a2a.A2AInvocationResult;
import com.zhouruojun.agentcore.a2a.A2aClientManager;
import com.zhouruojun.agentcore.agent.state.AgentMessageState;
import com.zhouruojun.agentcore.common.ContentFilter;
import com.zhouruojun.agentcore.config.AgentConstants;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElementHelper;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;

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
        String session = sessionId.get();

        String taskInstruction = getTaskInstruction(state);
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("message", taskInstruction);
        parameters.put("userTools", JSONObject.toJSONString(jsonTools));
        parameters.put("originalMessage", extractMessageContent(state.lastMessage().get()));

        log.info("Invoking agent {} with task instruction: {}", agentName, taskInstruction);
        A2AInvocationResult invocationResult = a2aClientManager.invokeAgent(agentName, AgentConstants.TASK_TYPE_CHAT, parameters, session);

        if (!invocationResult.success()) {
            String errorMessage = invocationResult.error() != null ? invocationResult.error() : "智能体服务不可用";
            log.error("Agent {} invocation failed: {}", agentName, errorMessage);
            return Map.of(
                    "next", "userInput",
                    "agent_response", "调用智能体失败: " + errorMessage,
                    "username", username.get()
            );
        }

        String filteredResponse = ContentFilter.filterThinkingContent(invocationResult.response());
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