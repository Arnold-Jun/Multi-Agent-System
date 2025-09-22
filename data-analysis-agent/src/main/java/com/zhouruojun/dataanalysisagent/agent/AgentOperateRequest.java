package com.zhouruojun.dataanalysisagent.agent;

import lombok.Data;

import java.util.List;

/**
 * Agent操作请求类
 * 与agent-core保持完全一致
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

