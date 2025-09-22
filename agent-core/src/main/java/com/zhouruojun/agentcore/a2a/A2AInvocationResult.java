package com.zhouruojun.agentcore.a2a;

/**
 * 封裝A2A遠程調用的結果，攜帶狀態碼與錯誤信息，便於上層做統一處理。
 */
public record A2AInvocationResult(boolean success, String response, String error, int statusCode) {

    public static A2AInvocationResult success(String response, int statusCode) {
        return new A2AInvocationResult(true, response, null, statusCode);
    }

    public static A2AInvocationResult failure(String error, int statusCode) {
        return new A2AInvocationResult(false, null, error, statusCode);
    }

    public String messageOrError() {
        return success ? response : error;
    }
}
