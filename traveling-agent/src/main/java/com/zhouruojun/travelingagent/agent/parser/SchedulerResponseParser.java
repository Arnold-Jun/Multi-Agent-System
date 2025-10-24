package com.zhouruojun.travelingagent.agent.parser;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.JSONObject;
import com.zhouruojun.travelingagent.agent.dto.SchedulerResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scheduler响应解析器
 */
@Slf4j
@Component
public class SchedulerResponseParser {

    // 有效的状态值
    private static final List<String> VALID_STATUSES = Arrays.asList(
        "in_progress", "completed", "failed", "retry"
    );
    
    // 有效的下一个节点值
    private static final List<String> VALID_NEXT_NODES = Arrays.asList(
        "metaSearchAgent", "itineraryPlannerAgent", "bookingAgent", "userInput", "planner", "summary"
    );

    /**
     * 解析Scheduler响应
     */
    public SchedulerResponse parseResponse(String response) {
        try {
            log.debug("Parsing scheduler response: {}", response);
            
            // 提取JSON部分
            String jsonStr = extractJsonFromResponse(response);
            if (jsonStr == null) {
                throw new IllegalArgumentException("No valid JSON found in response");
            }
            
            // 解析JSON
            JSONObject json = JSON.parseObject(jsonStr);
            
            // 构建响应对象
            SchedulerResponse schedulerResponse = new SchedulerResponse();
            
            // 解析taskUpdate
            if (json.containsKey("taskUpdate")) {
                JSONObject taskUpdateJson = json.getJSONObject("taskUpdate");
                SchedulerResponse.TaskUpdate taskUpdate = new SchedulerResponse.TaskUpdate();
                taskUpdate.setTaskId(taskUpdateJson.getString("taskId"));
                taskUpdate.setStatus(taskUpdateJson.getString("status"));
                taskUpdate.setReason(taskUpdateJson.getString("reason"));
                taskUpdate.setFailureCount(taskUpdateJson.getInteger("failureCount"));
                
                // 验证状态
                if (taskUpdate.getStatus() != null && !VALID_STATUSES.contains(taskUpdate.getStatus())) {
                    log.warn("Invalid status: {}, valid statuses: {}", taskUpdate.getStatus(), VALID_STATUSES);
                }
                
                schedulerResponse.setTaskUpdate(taskUpdate);
            }
            
            // 解析nextAction
            if (json.containsKey("nextAction")) {
                JSONObject nextActionJson = json.getJSONObject("nextAction");
                SchedulerResponse.NextAction nextAction = new SchedulerResponse.NextAction();
                nextAction.setNext(nextActionJson.getString("next"));
                nextAction.setTaskDescription(nextActionJson.getString("taskDescription"));
                nextAction.setContext(nextActionJson.getString("context"));
                
                // 验证下一个节点
                if (nextAction.getNext() != null && !VALID_NEXT_NODES.contains(nextAction.getNext())) {
                    log.warn("Invalid next node: {}, valid nodes: {}", nextAction.getNext(), VALID_NEXT_NODES);
                }
                
                schedulerResponse.setNextAction(nextAction);
            }
            
            log.debug("Parsed scheduler response: {}", schedulerResponse);
            return schedulerResponse;
            
        } catch (JSONException e) {
            log.error("JSON parsing error: {}", e.getMessage());
            throw new IllegalArgumentException("Invalid JSON format: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Error parsing scheduler response", e);
            throw new RuntimeException("Failed to parse scheduler response", e);
        }
    }

    /**
     * 从响应中提取JSON字符串
     */
    private String extractJsonFromResponse(String response) {
        if (response == null || response.trim().isEmpty()) {
            return null;
        }
        
        // 尝试直接解析整个响应
        try {
            JSON.parseObject(response);
            return response;
        } catch (JSONException e) {
            // 如果直接解析失败，尝试提取JSON部分
        }
        
        // 使用正则表达式提取JSON
        Pattern jsonPattern = Pattern.compile("\\{.*\\}", Pattern.DOTALL);
        Matcher matcher = jsonPattern.matcher(response);
        
        if (matcher.find()) {
            String jsonStr = matcher.group();
            try {
                JSON.parseObject(jsonStr);
                return jsonStr;
            } catch (JSONException e) {
                log.warn("Extracted string is not valid JSON: {}", jsonStr);
            }
        }
        
        return null;
    }

    /**
     * 验证解析后的响应
     */
    public boolean validateResponse(SchedulerResponse response) {
        if (response == null) {
            return false;
        }
        
        // 验证taskUpdate
        if (response.getTaskUpdate() != null) {
            SchedulerResponse.TaskUpdate taskUpdate = response.getTaskUpdate();
            if (taskUpdate.getStatus() != null && !VALID_STATUSES.contains(taskUpdate.getStatus())) {
                log.warn("Invalid task status: {}", taskUpdate.getStatus());
                return false;
            }
        }
        
        // 验证nextAction
        if (response.getNextAction() != null) {
            SchedulerResponse.NextAction nextAction = response.getNextAction();
            if (nextAction.getNext() != null && !VALID_NEXT_NODES.contains(nextAction.getNext())) {
                log.warn("Invalid next node: {}", nextAction.getNext());
                return false;
            }
        }
        
        return true;
    }
}

