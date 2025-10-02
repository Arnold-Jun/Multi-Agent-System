package com.zhouruojun.agentcore.a2a;

import lombok.Data;

/**
 * A2A调用结果封装
 */
@Data
public class A2AInvocationResult {
    private boolean success;
    private String response;
    private String error;
    private String taskId;
    private String sessionId;
    private Integer statusCode;

    public A2AInvocationResult(boolean success, String response, String error) {
        this.success = success;
        this.response = response;
        this.error = error;
    }

    public A2AInvocationResult(boolean success, String response, String error, String taskId, String sessionId) {
        this.success = success;
        this.response = response;
        this.error = error;
        this.taskId = taskId;
        this.sessionId = sessionId;
    }

    public A2AInvocationResult(boolean success, String response, String error, Integer statusCode) {
        this.success = success;
        this.response = response;
        this.error = error;
        this.statusCode = statusCode;
    }

    public static A2AInvocationResult success(String response) {
        return new A2AInvocationResult(true, response, null);
    }

    public static A2AInvocationResult success(String response, Integer statusCode) {
        return new A2AInvocationResult(true, response, null, statusCode);
    }

    public static A2AInvocationResult success(String response, String taskId, String sessionId) {
        return new A2AInvocationResult(true, response, null, taskId, sessionId);
    }

    public static A2AInvocationResult failure(String error) {
        return new A2AInvocationResult(false, null, error);
    }

    public static A2AInvocationResult failure(String error, Integer statusCode) {
        return new A2AInvocationResult(false, null, error, statusCode);
    }

    public static A2AInvocationResult failure(String error, String taskId, String sessionId) {
        return new A2AInvocationResult(false, null, error, taskId, sessionId);
    }

    // 添加便捷方法
    public boolean isSuccess() {
        return success;
    }

    public String getResponse() {
        return response;
    }

    public String getError() {
        return error;
    }
}
