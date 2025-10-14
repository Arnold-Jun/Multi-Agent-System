package com.zhouruojun.agentcore.agent.actions;

import com.alibaba.fastjson.JSONObject;
import com.zhouruojun.a2acore.spec.message.util.Uuid;
import com.zhouruojun.agentcore.agent.state.AgentMessageState;
import com.zhouruojun.agentcore.a2a.A2aClientManager;
import com.zhouruojun.agentcore.a2a.A2aTaskManager;
import com.zhouruojun.a2acore.spec.*;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Agent调用节点
 * 负责调用指定的智能体并处理返回结果
 */
@Slf4j
public class AgentInvoke implements NodeAction<AgentMessageState> {

    private final A2aClientManager a2aClientManager;
    public AgentInvoke(A2aClientManager a2aClientManager) {
        this.a2aClientManager = a2aClientManager;
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

        log.info("AgentInvoke state data: {}", JSONObject.toJSONString(data));
        log.info("nextAgent: {}, username: {}, sessionId: {}", nextAgent, username, sessionId);

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
        parameters.put("userTools", "[]"); // agent-core 不提供工具
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
        
        // 检查是否已经为这个会话发送了相同的任务（防止重复发送）
        if (isDuplicateTask(sessionId.get(), agentName, taskInstruction)) {
            log.warn("Duplicate task detected for session {} and agent {}, skipping", sessionId.get(), agentName);
            return Map.of(
                "next", "agentInvokeStateCheck",
                "nextAgent", agentName,
                "currentAgent", agentName,
                "agent_response", "任务已在进行中，请稍候..."
            );
        }
        
        // 使用A2A协议发送任务订阅（异步，不等待响应）
        String taskId = invokeAgentWithA2AAsync(agentName, taskInstruction, sessionId.get(), username.get());
        
        // 返回agentInvokeStateCheck，流程会在此之前interrupt等待
        Map<String, Object> result = Map.of(
                "next", "agentInvokeStateCheck",
                "nextAgent", agentName,  // 保持nextAgent为agentName，不要清空
                "currentAgent", agentName,
                "taskId", taskId
        );

        log.info("AgentInvoke sent task to {} with taskId: {}, will interrupt before agentInvokeStateCheck", agentName, taskId);
        return result;
    }

    /**
     * 使用A2A协议异步调用智能体（不等待响应，使用interrupt机制）
     */
    private String invokeAgentWithA2AAsync(String agentName, String taskInstruction, String sessionId, String userEmail) {
        try {
            // 构建A2A消息
            TextPart textPart = new TextPart();
            textPart.setText(taskInstruction);
            
            Message message = Message.builder()
                .role(Role.USER)
                .parts(Arrays.asList(textPart))
                .metadata(Map.of(
                    "method", "chat",
                    "sessionId", sessionId,
                    "userEmail", userEmail
                ))
                .build();
            
            // 生成任务ID
            String taskId = Uuid.uuid4hex();
            
            // 获取Agent Card以确定支持的输出模式
            AgentCard agentCard = a2aClientManager.getA2aClient(agentName).getAgentCard();
            List<String> acceptedOutputModes = getAgentAcceptedOutputModes(agentCard);
            
            // 获取push notification配置
            PushNotificationConfig pushNotificationConfig = a2aClientManager.getA2aClient(agentName).getPushNotificationConfig();
            log.info("Push notification config for {}: {}", agentName, 
                pushNotificationConfig != null ? pushNotificationConfig.getUrl() : "null");
            
            // 构建任务参数
            TaskSendParams taskSendParams = TaskSendParams.builder()
                .id(taskId)
                .sessionId(sessionId)
                .message(message)
                .acceptedOutputModes(acceptedOutputModes)
                .pushNotification(pushNotificationConfig)
                .metadata(Map.of(
                    "method", "chat",
                    "userEmail", userEmail,
                    "userTools", "[]"
                ))
                .historyLength(1)
                .build();
            
            // 异步发送流式任务订阅（不等待响应）
            a2aClientManager.sendTaskSubscribe(agentName, taskSendParams);
            
            // 保存任务参数用于后续处理
            A2aTaskManager.getInstance().saveTaskParams(taskSendParams);
            
            log.info("Sent A2A task subscription to {} with taskId: {}", agentName, taskId);
            
            return taskId;
            
        } catch (Exception e) {
            log.error("A2A异步调用失败", e);
            throw new RuntimeException("A2A调用异常: " + e.getMessage(), e);
        }
    }
    
    /**
     * 获取Agent支持的输出模式
     */
    private List<String> getAgentAcceptedOutputModes(AgentCard agentCard) {
        List<String> systemSupportModes = a2aClientManager.getSystemSupportModes();
        return agentCard.getDefaultOutputModes().stream()
                .filter(systemSupportModes::contains)
                .toList();
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
    
    /**
     * 检查是否为重复任务
     * 防止同一个会话在短时间内发送相同的任务给同一个智能体
     */
    private boolean isDuplicateTask(String sessionId, String agentName, String taskInstruction) {
        try {
            // 获取会话的所有任务
            List<String> sessionTaskIds = A2aTaskManager.getInstance().getSessionTaskIds(sessionId);
            if (sessionTaskIds.isEmpty()) {
                return false;
            }
            
            // 检查最近的任务（最多检查最近3个任务）
            int checkCount = Math.min(3, sessionTaskIds.size());
            for (int i = sessionTaskIds.size() - checkCount; i < sessionTaskIds.size(); i++) {
                String taskId = sessionTaskIds.get(i);
                TaskSendParams taskParams = A2aTaskManager.getInstance().getTaskParams(taskId);
                
                if (taskParams != null) {
                    // 检查是否为同一个智能体
                    String taskAgentName = extractAgentNameFromTask(taskParams);
                    if (agentName.equals(taskAgentName)) {
                        // 检查任务指令是否相似（简单的字符串比较）
                        String existingInstruction = extractTaskInstruction(taskParams);
                        if (isSimilarTask(taskInstruction, existingInstruction)) {
                            log.warn("Found similar task: existing='{}', new='{}'", existingInstruction, taskInstruction);
                            return true;
                        }
                    }
                }
            }
            
            return false;
        } catch (Exception e) {
            log.error("Error checking duplicate task", e);
            return false; // 出错时不阻止任务执行
        }
    }
    
    /**
     * 从任务参数中提取智能体名称
     */
    private String extractAgentNameFromTask(TaskSendParams taskParams) {
        // 这里需要根据实际的TaskSendParams结构来提取智能体名称
        // 暂时返回空字符串，需要根据实际情况调整
        return "";
    }
    
    /**
     * 从任务参数中提取任务指令
     */
    private String extractTaskInstruction(TaskSendParams taskParams) {
        try {
            if (taskParams.getMessage() != null && 
                taskParams.getMessage().getParts() != null && 
                !taskParams.getMessage().getParts().isEmpty()) {
                
                for (com.zhouruojun.a2acore.spec.Part part : taskParams.getMessage().getParts()) {
                    if (part instanceof com.zhouruojun.a2acore.spec.TextPart) {
                        return ((com.zhouruojun.a2acore.spec.TextPart) part).getText();
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Error extracting task instruction", e);
        }
        return "";
    }
    
    /**
     * 检查两个任务是否相似
     */
    private boolean isSimilarTask(String task1, String task2) {
        if (task1 == null || task2 == null) {
            return false;
        }
        
        // 简单的相似度检查：去除空格后比较
        String normalized1 = task1.trim().replaceAll("\\s+", " ");
        String normalized2 = task2.trim().replaceAll("\\s+", " ");
        
        // 如果完全相同，认为是重复任务
        if (normalized1.equals(normalized2)) {
            return true;
        }
        
        // 如果长度差异很大，认为不是重复任务
        if (Math.abs(normalized1.length() - normalized2.length()) > normalized1.length() * 0.3) {
            return false;
        }
        
        // 简单的编辑距离检查（这里可以优化为更复杂的算法）
        return calculateSimilarity(normalized1, normalized2) > 0.8;
    }
    
    /**
     * 计算两个字符串的相似度（简单的Jaccard相似度）
     */
    private double calculateSimilarity(String s1, String s2) {
        if (s1.length() == 0 && s2.length() == 0) {
            return 1.0;
        }
        
        Set<String> set1 = new HashSet<>(Arrays.asList(s1.split(" ")));
        Set<String> set2 = new HashSet<>(Arrays.asList(s2.split(" ")));
        
        Set<String> intersection = new HashSet<>(set1);
        intersection.retainAll(set2);
        
        Set<String> union = new HashSet<>(set1);
        union.addAll(set2);
        
        return (double) intersection.size() / union.size();
    }

}