package com.zhouruojun.jobsearchagent.agent.parser;

import com.alibaba.fastjson.JSONObject;
import com.zhouruojun.jobsearchagent.agent.dto.SchedulerResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * Scheduler响应解析器
 * 负责解析和验证Scheduler输出的JSON格式
 */
@Slf4j
@Component
public class SchedulerResponseParser {
    
    // 支持的任务状态
    private static final List<String> VALID_TASK_STATUS = Arrays.asList(
        "in_progress", "completed", "failed", "retry"
    );
    
    // 支持的下一个节点
    private static final List<String> VALID_NEXT_NODES = Arrays.asList(
        "jobInfoCollectorAgent",
        "resumeAnalysisOptimizationAgent",
        "jobSearchExecutionAgent",
        "planner",
        "summary"
    );
    
    /**
     * 解析Scheduler响应
     */
    public ParseResult parseSchedulerResponse(String responseText) {
        try {
            log.info("开始解析Scheduler响应: {}", responseText);
            
            // 提取JSON部分
            String jsonText = extractJsonFromResponse(responseText);
            if (jsonText == null) {
                return ParseResult.failure("未找到有效的JSON格式");
            }
            
            // 解析JSON
            SchedulerResponse response = JSONObject.parseObject(jsonText, SchedulerResponse.class);
            
            // 验证响应
            ValidationResult validation = validateResponse(response);
            if (!validation.isValid()) {
                return ParseResult.failure(validation.getErrorMessage());
            }
            
            log.info("Scheduler响应解析成功: taskId={}, status={}, next={}", 
                response.getTaskUpdate().getTaskId(),
                response.getTaskUpdate().getStatus(),
                response.getNextAction().getNext());
            
            return ParseResult.success(response);
            
        } catch (Exception e) {
            log.error("解析Scheduler响应时发生错误: {}", e.getMessage(), e);
            return ParseResult.failure("JSON解析错误: " + e.getMessage());
        }
    }
    
    /**
     * 从响应文本中提取JSON部分
     */
    private String extractJsonFromResponse(String responseText) {
        if (responseText == null || responseText.trim().isEmpty()) {
            return null;
        }
        
        // 查找JSON开始和结束位置
        int jsonStart = responseText.indexOf("{");
        int jsonEnd = responseText.lastIndexOf("}");
        
        if (jsonStart == -1 || jsonEnd == -1 || jsonStart >= jsonEnd) {
            return null;
        }
        
        return responseText.substring(jsonStart, jsonEnd + 1);
    }
    
    /**
     * 验证响应格式
     */
    private ValidationResult validateResponse(SchedulerResponse response) {
        if (response == null) {
            return ValidationResult.invalid("响应对象为空");
        }
        
        // 验证taskUpdate
        if (response.getTaskUpdate() == null) {
            return ValidationResult.invalid("缺少taskUpdate字段");
        }
        
        SchedulerResponse.TaskUpdate taskUpdate = response.getTaskUpdate();
        if (taskUpdate.getTaskId() == null || taskUpdate.getTaskId().trim().isEmpty()) {
            return ValidationResult.invalid("taskUpdate.taskId不能为空");
        }
        
        if (taskUpdate.getStatus() == null || !VALID_TASK_STATUS.contains(taskUpdate.getStatus())) {
            return ValidationResult.invalid("taskUpdate.status必须是: " + VALID_TASK_STATUS);
        }
        
        // 验证nextAction
        if (response.getNextAction() == null) {
            return ValidationResult.invalid("缺少nextAction字段");
        }
        
        SchedulerResponse.NextAction nextAction = response.getNextAction();
        if (nextAction.getNext() == null || !VALID_NEXT_NODES.contains(nextAction.getNext())) {
            return ValidationResult.invalid("nextAction.next必须是: " + VALID_NEXT_NODES);
        }
        
        if (nextAction.getTaskDescription() == null || nextAction.getTaskDescription().trim().isEmpty()) {
            return ValidationResult.invalid("nextAction.taskDescription不能为空");
        }
        
        if (nextAction.getContext() == null || nextAction.getContext().trim().isEmpty()) {
            return ValidationResult.invalid("nextAction.context不能为空");
        }
        
        return ValidationResult.valid();
    }
    
    /**
     * 解析结果
     */
    public static class ParseResult {
        private final boolean success;
        private final SchedulerResponse response;
        private final String errorMessage;
        
        private ParseResult(boolean success, SchedulerResponse response, String errorMessage) {
            this.success = success;
            this.response = response;
            this.errorMessage = errorMessage;
        }
        
        public static ParseResult success(SchedulerResponse response) {
            return new ParseResult(true, response, null);
        }
        
        public static ParseResult failure(String errorMessage) {
            return new ParseResult(false, null, errorMessage);
        }
        
        public boolean isSuccess() {
            return success;
        }
        
        public SchedulerResponse getResponse() {
            return response;
        }
        
        public String getErrorMessage() {
            return errorMessage;
        }
    }
    
    /**
     * 验证结果
     */
    private static class ValidationResult {
        private final boolean valid;
        private final String errorMessage;
        
        private ValidationResult(boolean valid, String errorMessage) {
            this.valid = valid;
            this.errorMessage = errorMessage;
        }
        
        public static ValidationResult valid() {
            return new ValidationResult(true, null);
        }
        
        public static ValidationResult invalid(String errorMessage) {
            return new ValidationResult(false, errorMessage);
        }
        
        public boolean isValid() {
            return valid;
        }
        
        public String getErrorMessage() {
            return errorMessage;
        }
    }
}

