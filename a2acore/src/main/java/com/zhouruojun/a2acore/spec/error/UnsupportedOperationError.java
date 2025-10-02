package com.zhouruojun.a2acore.spec.error;

/**
 * <p>
 *     <a href="https://github.com/google/A2A/blob/main/docs/specification.md#8-error-handling">µã»÷Ìø×ª</a>
 * </p>
 *
 */
public class UnsupportedOperationError extends JsonRpcError {
    public UnsupportedOperationError() {
        this.setCode(-32004);
        this.setMessage("This operation is not supported");
    }
}


