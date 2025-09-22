package com.zhouruojun.agentcore.spec.error;

/**
 * <p>
 *      <a href="https://github.com/google/A2A/blob/main/docs/specification.md#8-error-handling">点击跳转</a>
 * </p>
 *
 */
public class AuthorizationFailedError extends JsonRpcError {
    public AuthorizationFailedError() {
        this.setCode(-32008);
        this.setMessage("Authorization failed");
    }
}
