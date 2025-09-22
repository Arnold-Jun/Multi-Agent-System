package com.zhouruojun.agentcore.spec.error;

/**
 * <p>
 *     <a href="https://github.com/google/A2A/blob/main/docs/specification.md#8-error-handling">点击跳转</a>
 * </p>
 *
 */
public class InvalidRequestError extends JsonRpcError {
    public InvalidRequestError() {
        this.setCode(-32600);
        this.setMessage("Request payload validation error");
    }
}
