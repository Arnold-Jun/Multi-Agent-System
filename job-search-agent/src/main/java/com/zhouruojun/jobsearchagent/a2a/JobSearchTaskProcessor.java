package com.zhouruojun.jobsearchagent.a2a;

import com.zhouruojun.jobsearchagent.agent.core.JobSearchControllerCore;
import com.zhouruojun.jobsearchagent.agent.dto.AgentChatRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 求职任务处理器
 * 处理来自其他智能体的A2A请求
 */
@Component
@Slf4j
public class JobSearchTaskProcessor {

    @Autowired
    private JobSearchControllerCore jobSearchControllerCore;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 异步处理求职任务
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
     * 同步处理求职任务
     */
    public String processTask(String taskType, JsonNode parameters, String sessionId) {
        try {
            switch (taskType.toLowerCase()) {
                case "job_search":
                case "search_jobs":
                    return handleJobSearchTask(parameters, sessionId);

                case "resume_analysis":
                case "analyze_resume":
                    return handleResumeAnalysisTask(parameters, sessionId);
                
                case "matching_analysis":
                case "job_matching":
                    return handleMatchingAnalysisTask(parameters, sessionId);
                
                case "career_advice":
                case "career_guidance":
                    return handleCareerAdviceTask(parameters, sessionId);
                
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

    private String handleJobSearchTask(JsonNode parameters, String sessionId) {
        String message = parameters.path("message").asText();
        if (message.isEmpty()) {
            return createErrorResponse("缺少搜索消息");
        }
        
        // 创建AgentChatRequest对象
        AgentChatRequest chatRequest = new AgentChatRequest();
        chatRequest.setSessionId(sessionId);
        chatRequest.setChat(message);
        
        return jobSearchControllerCore.processStreamReturnStr(chatRequest);
    }

    private String handleResumeAnalysisTask(JsonNode parameters, String sessionId) {
        String resumePath = parameters.path("resumePath").asText();
        String message = parameters.path("message").asText();
        
        if (resumePath.isEmpty() && message.isEmpty()) {
            return createErrorResponse("缺少简历路径或分析消息");
        }
        
        // 构建分析请求
        String analysisMessage = !message.isEmpty() ? message : "请分析这份简历";
        if (!resumePath.isEmpty()) {
            analysisMessage += "，简历路径: " + resumePath;
        }
        
        AgentChatRequest chatRequest = new AgentChatRequest();
        chatRequest.setSessionId(sessionId);
        chatRequest.setChat(analysisMessage);
        
        return jobSearchControllerCore.processStreamReturnStr(chatRequest);
    }

    private String handleMatchingAnalysisTask(JsonNode parameters, String sessionId) {
        String resumeInfo = parameters.path("resumeInfo").asText();
        String jobInfo = parameters.path("jobInfo").asText();
        
        if (resumeInfo.isEmpty() || jobInfo.isEmpty()) {
            return createErrorResponse("缺少简历信息或岗位信息");
        }
        
        String message = String.format("请分析简历与岗位的匹配度。简历信息: %s，岗位信息: %s", resumeInfo, jobInfo);
        
        AgentChatRequest chatRequest = new AgentChatRequest();
        chatRequest.setSessionId(sessionId);
        chatRequest.setChat(message);
        
        return jobSearchControllerCore.processStreamReturnStr(chatRequest);
    }

    private String handleCareerAdviceTask(JsonNode parameters, String sessionId) {
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
        
        return jobSearchControllerCore.processStreamReturnStr(chatRequest);
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
        
        return jobSearchControllerCore.processStreamReturnStr(chatRequest);
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
        enhancedMessage.append("请根据上述任务指令执行求职分析任务。");
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
