package com.zhouruojun.dataanalysisagent.a2a;

import com.zhouruojun.dataanalysisagent.agent.core.DataAnalysisControllerCore;
import com.zhouruojun.dataanalysisagent.agent.AgentChatRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 数据分析任务处理器
 * 处理来自其他智能体的A2A请求
 */
@Component
@Slf4j
public class DataAnalysisTaskProcessor {

    @Autowired
    private DataAnalysisControllerCore dataAnalysisControllerCore;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 异步处理数据分析任务
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
     * 同步处理数据分析任务
     */
    public String processTask(String taskType, JsonNode parameters, String sessionId) {
        try {
            switch (taskType.toLowerCase()) {
                case "data_analysis":
                case "analyze_data":
                    return handleDataAnalysisTask(parameters, sessionId);
                
                case "load_data":
                case "data_loading":
                    return handleDataLoadingTask(parameters);
                
                case "generate_chart":
                case "visualization":
                    return handleVisualizationTask(parameters);
                
                case "statistics":
                case "statistical_analysis":
                    return handleStatisticsTask(parameters);
                
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

    private String handleDataAnalysisTask(JsonNode parameters, String sessionId) {
        String message = parameters.path("message").asText();
        if (message.isEmpty()) {
            return createErrorResponse("缺少分析消息");
        }
        
        // 创建AgentChatRequest对象
        AgentChatRequest chatRequest = new AgentChatRequest();
        chatRequest.setSessionId(sessionId);
        chatRequest.setChat(message);
        
        return dataAnalysisControllerCore.processStreamReturnStr(chatRequest);
    }

    private String handleDataLoadingTask(JsonNode parameters) {
        String filePath = parameters.path("filePath").asText();
        String format = parameters.path("format").asText("csv");
        boolean hasHeader = parameters.path("hasHeader").asBoolean(true);
        
        if (filePath.isEmpty()) {
            return createErrorResponse("缺少文件路径");
        }
        
        // 使用图处理流程处理数据加载任务
        String message = String.format("请加载文件: %s, 格式: %s, 包含表头: %s", filePath, format, hasHeader);
        AgentChatRequest chatRequest = new AgentChatRequest();
        chatRequest.setSessionId("data-loading-" + System.currentTimeMillis());
        chatRequest.setChat(message);
        
        return dataAnalysisControllerCore.processStreamReturnStr(chatRequest);
    }

    private String handleVisualizationTask(JsonNode parameters) {
        String chartType = parameters.path("chartType").asText();
        String title = parameters.path("title").asText();
        JsonNode dataNode = parameters.path("data");
        
        if (chartType.isEmpty() || title.isEmpty() || dataNode.isMissingNode()) {
            return createErrorResponse("缺少必要的图表参数");
        }
        
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = objectMapper.convertValue(dataNode, Map.class);
            
            // 使用图处理流程处理可视化任务
            String message = String.format("请生成%s图表，标题: %s，数据: %s", chartType, title, data.toString());
            AgentChatRequest chatRequest = new AgentChatRequest();
            chatRequest.setSessionId("visualization-" + System.currentTimeMillis());
            chatRequest.setChat(message);
            
            return dataAnalysisControllerCore.processStreamReturnStr(chatRequest);
        } catch (Exception e) {
            return createErrorResponse("解析图表数据失败: " + e.getMessage());
        }
    }

    private String handleStatisticsTask(JsonNode parameters) {
        // 这里可以扩展为更复杂的统计分析任务
        return createSuccessResponse("统计分析功能正在开发中");
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
        
        return dataAnalysisControllerCore.processStreamReturnStr(chatRequest);
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
        enhancedMessage.append("请根据上述任务指令执行数据分析任务。");
        enhancedMessage.append("如果需要更多信息，请主动使用搜索工具获取最新数据。");
        enhancedMessage.append("请提供详细的分析步骤和结果。");
        
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
