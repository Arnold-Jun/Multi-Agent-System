package com.zhouruojun.jobsearchagent.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Scheduler响应数据模型
 */
@Data
public class SchedulerResponse {
    
    @JsonProperty("taskUpdate")
    private TaskUpdate taskUpdate;
    
    @JsonProperty("nextAction")
    private NextAction nextAction;
    
    @Data
    public static class TaskUpdate {
        @JsonProperty("taskId")
        private String taskId;
        
        @JsonProperty("status")
        private String status; // "completed", "failed", "replan", "retry"
        
        @JsonProperty("reason")
        private String reason;
        
        @JsonProperty("failureCount")
        private Integer failureCount;
    }
    
    @Data
    public static class NextAction {
        @JsonProperty("next")
        private String next; // 子图名称或"planner"
        
        @JsonProperty("taskDescription")
        private String taskDescription;
        
        @JsonProperty("context")
        private String context;
    }
}

