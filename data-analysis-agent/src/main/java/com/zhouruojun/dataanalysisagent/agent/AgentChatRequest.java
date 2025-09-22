package com.zhouruojun.dataanalysisagent.agent;

import lombok.Data;

import java.util.List;

/**
 * Agent聊天请求类
 * 与agent-core保持完全一致
 */
@Data
public class AgentChatRequest {
    
    private String chat;
    private String sessionId;
    private List<AgentOperateRequest> operates;
    private List<Object> userTools;

    public AgentChatRequest() {}

    public AgentChatRequest(String chat, String sessionId) {
        this.chat = chat;
        this.sessionId = sessionId;
    }
}

