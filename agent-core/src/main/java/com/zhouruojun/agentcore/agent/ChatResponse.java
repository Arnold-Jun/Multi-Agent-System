package com.zhouruojun.agentcore.agent;

import lombok.Data;

/**
 * 标准聊天响应类
 */
@Data
public class ChatResponse {
    
    private String response;
    private String sessionId;
    private boolean success;
    private String error;

    public ChatResponse() {}

    public ChatResponse(String response, String sessionId) {
        this.response = response;
        this.sessionId = sessionId;
        this.success = true;
    }

    public static ChatResponse success(String response, String sessionId) {
        return new ChatResponse(response, sessionId);
    }

    public static ChatResponse error(String error, String sessionId) {
        ChatResponse response = new ChatResponse();
        response.setError(error);
        response.setSessionId(sessionId);
        response.setSuccess(false);
        return response;
    }
}
