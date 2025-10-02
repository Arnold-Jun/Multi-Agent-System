package com.zhouruojun.a2acore.spec.error;

/**
 * <p>
 *     <a href="https://github.com/google/A2A/blob/main/docs/specification.md#8-error-handling">µã»÷Ìø×ª</a>
 * </p>
 *
 */
public class ResourceUnavailableError extends JsonRpcError {
    public ResourceUnavailableError() {
        this.setCode(-32011);
        this.setMessage("A required resource is unavailable");
    }
}


