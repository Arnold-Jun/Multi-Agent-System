package com.zhouruojun.a2acore.spec.error;

/**
 * <p>
 *      <a href="https://github.com/google/A2A/blob/main/docs/specification.md#8-error-handling">�����ת</a>
 * </p>
 *
 */
public class AuthorizationFailedError extends JsonRpcError {
    public AuthorizationFailedError() {
        this.setCode(-32008);
        this.setMessage("Authorization failed");
    }
}


