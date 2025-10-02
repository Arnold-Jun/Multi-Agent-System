package com.zhouruojun.a2acore.spec.error;

/**
 * <p>
 *     <a href="https://github.com/google/A2A/blob/main/docs/specification.md#8-error-handling">µã»÷Ìø×ª</a>
 * </p>
 *
 */
public class InvalidRequestError extends JsonRpcError {
    public InvalidRequestError() {
        this.setCode(-32600);
        this.setMessage("Request payload validation error");
    }
}


