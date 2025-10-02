package com.zhouruojun.a2acore.spec.error;

/**
 * <p>
 *      <a href="https://github.com/google/A2A/blob/main/docs/specification.md#8-error-handling">µã»÷Ìø×ª</a>
 * </p>
 *
 */
public class RateLimitExceededError extends JsonRpcError {
    public RateLimitExceededError() {
        this.setCode(-32010);
        this.setMessage("Rate limit exceeded");
    }
}


