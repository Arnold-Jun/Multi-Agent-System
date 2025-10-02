package com.zhouruojun.a2acore.spec.error;

/**
 * <p>
 *     <a href="https://github.com/google/A2A/blob/main/docs/specification.md#8-error-handling">µã»÷Ìø×ª</a>
 * </p>
 *
 */
public class MethodNotFoundError extends JsonRpcError {
    public MethodNotFoundError() {
        this.setCode(-32601);
        this.setMessage("Method not found");
    }
}


