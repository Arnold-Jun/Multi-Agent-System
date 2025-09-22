package com.zhouruojun.agentcore.spec.error;

/**
 * <p>
 *     <a href="https://github.com/google/A2A/blob/main/docs/specification.md#8-error-handling">点击跳转</a>
 * </p>
 *
 */
public class JSONParseError extends JsonRpcError {
    public JSONParseError() {
        this.setCode(-32700);
        this.setMessage("Invalid JSON payload");
    }
}
