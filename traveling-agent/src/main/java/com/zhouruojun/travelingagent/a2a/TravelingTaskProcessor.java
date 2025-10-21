package com.zhouruojun.travelingagent.a2a;

import com.zhouruojun.travelingagent.agent.core.TravelingControllerCore;
import com.zhouruojun.travelingagent.agent.dto.AgentChatRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 旅游任务处理器
 * 处理来自其他智能体的A2A请求
 */
@Component
@Slf4j
public class TravelingTaskProcessor {

    @Autowired
    private TravelingControllerCore travelingControllerCore;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 异步处理旅游任务
     */
    public CompletableFuture<String> processTaskAsync(String taskType, JsonNode parameters, String sessionId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("Processing async task: {} for session: {}", taskType, sessionId);
                return processTask(taskType, parameters, sessionId);
            } catch (Exception e) {
                log.error("Error processing async task: {}", taskType, e);
                return createErrorResponse("处理任务时发生错误: " + e.getMessage());
            }
        });
    }

    /**
     * 同步处理旅游任务
     */
    public String processTask(String taskType, JsonNode parameters, String sessionId) {
        try {
            switch (taskType.toLowerCase()) {
                case "travel_planning":
                case "plan_travel":
                    return handleTravelPlanningTask(parameters, sessionId);

                case "travel_info_collection":
                case "collect_travel_info":
                    return handleTravelInfoCollectionTask(parameters, sessionId);
                
                case "travel_booking":
                case "book_travel":
                    return handleTravelBookingTask(parameters, sessionId);
                
                case "travel_advice":
                case "travel_guidance":
                    return handleTravelAdviceTask(parameters, sessionId);
                
                case "chat":
                case "conversation":
                    return handleChatTask(parameters, sessionId);
                
                default:
                    return createErrorResponse("不支持的任务类型: " + taskType);
            }
        } catch (Exception e) {
            log.error("Error processing task: {}", taskType, e);
            return createErrorResponse("处理任务时发生错误: " + e.getMessage());
        }
    }

    private String handleTravelPlanningTask(JsonNode parameters, String sessionId) {
        String message = parameters.path("message").asText();
        if (message.isEmpty()) {
            return createErrorResponse("缺少旅游规划消息");
        }
        
        // 创建AgentChatRequest对象
        AgentChatRequest chatRequest = new AgentChatRequest();
        chatRequest.setSessionId(sessionId);
        chatRequest.setChat(message);
        
        return travelingControllerCore.processStreamReturnStr(chatRequest);
    }

    private String handleTravelInfoCollectionTask(JsonNode parameters, String sessionId) {
        String destination = parameters.path("destination").asText();
        String message = parameters.path("message").asText();
        
        if (destination.isEmpty() && message.isEmpty()) {
            return createErrorResponse("缺少目的地或收集消息");
        }
        
        // 构建收集请求
        String collectionMessage = !message.isEmpty() ? message : "请收集旅游信息";
        if (!destination.isEmpty()) {
            collectionMessage += "，目的地: " + destination;
        }
        
        AgentChatRequest chatRequest = new AgentChatRequest();
        chatRequest.setSessionId(sessionId);
        chatRequest.setChat(collectionMessage);
        
        return travelingControllerCore.processStreamReturnStr(chatRequest);
    }

    private String handleTravelBookingTask(JsonNode parameters, String sessionId) {
        String travelPlan = parameters.path("travelPlan").asText();
        String bookingInfo = parameters.path("bookingInfo").asText();
        
        if (travelPlan.isEmpty() || bookingInfo.isEmpty()) {
            return createErrorResponse("缺少旅游计划或预订信息");
        }
        
        String message = String.format("请执行旅游预订。旅游计划: %s，预订信息: %s", travelPlan, bookingInfo);
        
        AgentChatRequest chatRequest = new AgentChatRequest();
        chatRequest.setSessionId(sessionId);
        chatRequest.setChat(message);
        
        return travelingControllerCore.processStreamReturnStr(chatRequest);
    }

    private String handleTravelAdviceTask(JsonNode parameters, String sessionId) {
        String message = parameters.path("message").asText();
        String originalMessage = parameters.path("originalMessage").asText("");
        
        if (message.isEmpty()) {
            return createErrorResponse("缺少建议请求消息");
        }
        
        // 记录接收到的任务指令信息
        log.info("Received task instruction: {}", message);
        if (!originalMessage.isEmpty()) {
            log.info("Original user message: {}", originalMessage);
        }
        
        // 构建增强的分析请求，包含任务指令和原始消息
        String enhancedMessage = buildEnhancedMessage(message, originalMessage);
        
        // 创建AgentChatRequest对象
        AgentChatRequest chatRequest = new AgentChatRequest();
        chatRequest.setSessionId(sessionId);
        chatRequest.setChat(enhancedMessage);
        
        return travelingControllerCore.processStreamReturnStr(chatRequest);
    }

    private String handleChatTask(JsonNode parameters, String sessionId) {
        String message = parameters.path("message").asText();
        String originalMessage = parameters.path("originalMessage").asText("");
        
        if (message.isEmpty()) {
            return createErrorResponse("缺少聊天消息");
        }
        
        // 记录接收到的任务指令信息
        log.info("Received task instruction: {}", message);
        if (!originalMessage.isEmpty()) {
            log.info("Original user message: {}", originalMessage);
        }
        
        // 构建增强的分析请求，包含任务指令和原始消息
        String enhancedMessage = buildEnhancedMessage(message, originalMessage);
        
        // 创建AgentChatRequest对象
        AgentChatRequest chatRequest = new AgentChatRequest();
        chatRequest.setSessionId(sessionId);
        chatRequest.setChat(enhancedMessage);
        
        return travelingControllerCore.processStreamReturnStr(chatRequest);
    }
    
    /**
     * 构建增强的分析请求消息
     * 结合任务指令和原始用户消息，提供更完整的上下文
     */
    private String buildEnhancedMessage(String taskInstruction, String originalMessage) {
        StringBuilder enhancedMessage = new StringBuilder();
        
        // 添加任务指令
        enhancedMessage.append("任务指令: ").append(taskInstruction).append("\n\n");
        
        // 如果有原始消息，添加作为参考
        if (!originalMessage.isEmpty() && !originalMessage.equals(taskInstruction)) {
            enhancedMessage.append("用户原始请求: ").append(originalMessage).append("\n\n");
        }
        
        // 添加执行指导
        enhancedMessage.append("请根据上述任务指令执行旅游规划任务。");
        enhancedMessage.append("如果需要更多信息，请主动使用搜索工具获取最新数据。");
        enhancedMessage.append("请提供详细的规划步骤和结果。");
        
        return enhancedMessage.toString();
    }

    private String createSuccessResponse(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", message);
        response.put("timestamp", System.currentTimeMillis());
        
        try {
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            return "{\"status\":\"success\",\"message\":\"" + message + "\"}";
        }
    }

    private String createErrorResponse(String error) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "error");
        response.put("error", error);
        response.put("timestamp", System.currentTimeMillis());
        
        try {
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            return "{\"status\":\"error\",\"error\":\"" + error + "\"}";
        }
    }
}

