package com.xiaohongshu.codewiz.codewizagent.agentcore.session.json;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class JsonRpcError {
    private int code;
    private String message;
    private Object data;

    // 标准错误码（JSON-RPC 2.0 规范）
    public static JsonRpcError invalidRequest() {
        return new JsonRpcError(-32600, "Invalid Request", null);
    }
    public static JsonRpcError methodNotFound() {
        return new JsonRpcError(-32601, "Method not found", null);
    }
    public static JsonRpcError authNotAllowed() {
        return new JsonRpcError(401, "Authentication required", null);
    }
}