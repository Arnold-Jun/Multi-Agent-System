package com.zhouruojun.agentcore.spec.error;

/**
 * <p>
 *     <a href="https://github.com/google/A2A/blob/main/docs/specification.md#8-error-handling">点击跳转</a>
 * </p>
 *
 */
public class MethodNotFoundError extends JsonRpcError {
    public MethodNotFoundError() {
        this.setCode(-32601);
        this.setMessage("Method not found");
    }
}
