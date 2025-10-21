package com.zhouruojun.travelingagent.agent.dto;

import lombok.Data;

/**
 * Agent操作请求类
 */
@Data
public class AgentOperateRequest {
    private String operation;
    private String data;
    private String sessionId;
}

