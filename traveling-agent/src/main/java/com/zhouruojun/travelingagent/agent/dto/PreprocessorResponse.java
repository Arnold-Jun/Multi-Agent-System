package com.zhouruojun.travelingagent.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Preprocessor响应数据模型
 */
@Data
public class PreprocessorResponse {
    
    @JsonProperty("next")
    private String next; // "planner", "Finish", "action", "userInput"
    
    @JsonProperty("output")
    private String output; // 任务描述或最终回复
}
