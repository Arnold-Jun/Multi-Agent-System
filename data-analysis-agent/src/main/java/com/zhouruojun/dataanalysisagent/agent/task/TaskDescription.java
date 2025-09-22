package com.zhouruojun.dataanalysisagent.agent.task;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 任务描述类
 * 用于在智能体之间传递结构化的任务信息
 */
@Data
public class TaskDescription {
    
    /**
     * 任务查询内容
     */
    @JsonProperty("query")
    private String query;
    
    /**
     * 任务目的和期望结果
     */
    @JsonProperty("purpose")
    private String purpose;
    
    /**
     * 相关上下文信息
     */
    @JsonProperty("context")
    private String context;
    

    /**
     * 默认构造函数
     */
    public TaskDescription() {
    }
    
    /**
     * 构造函数
     */
    public TaskDescription(String query, String purpose, String context) {
        this.query = query;
        this.purpose = purpose;
        this.context = context;
    }
    
    /**
     * 创建简单的任务描述
     */
    public static TaskDescription of(String query, String purpose) {
        return new TaskDescription(query, purpose, null);
    }
    
    /**
     * 创建完整的任务描述
     */
    public static TaskDescription of(String query, String purpose, String context) {
        return new TaskDescription(query, purpose, context);
    }
}
