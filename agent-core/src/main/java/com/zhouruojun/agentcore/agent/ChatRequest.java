package com.zhouruojun.agentcore.agent;

import lombok.Data;

/**
 * 标准聊天请求类
 */
@Data
public class ChatRequest {
    
    private String message;
    private String sessionId;

    public ChatRequest() {}

    public ChatRequest(String message, String sessionId) {
        this.message = message;
        this.sessionId = sessionId;
    }
}
