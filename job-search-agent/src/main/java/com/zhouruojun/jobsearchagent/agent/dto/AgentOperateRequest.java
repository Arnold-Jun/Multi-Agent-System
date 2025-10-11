package com.zhouruojun.jobsearchagent.agent.dto;

import lombok.Data;

import java.util.List;

/**
 * Agent操作请求类
 */
@Data
public class AgentOperateRequest {
    
    private String operateType;
    private List<String> text;

    public AgentOperateRequest() {}

    public AgentOperateRequest(String operateType, List<String> text) {
        this.operateType = operateType;
        this.text = text;
    }
}
