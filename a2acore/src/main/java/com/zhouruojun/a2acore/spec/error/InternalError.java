package com.zhouruojun.a2acore.spec.error;

/**
 * <p>
 *     <a href="https://github.com/google/A2A/blob/main/docs/specification.md#8-error-handling">µã»÷Ìø×ª</a>
 * </p>
 *
 */
public class InternalError extends JsonRpcError {
    public InternalError() {
        this.setCode(-32603);
        this.setMessage("Internal error");
    }

    public InternalError(String message) {
        this();
        this.setMessage(message);
    }
}


