package com.zhouruojun.a2acore.spec.error;

/**
 * <p>
 *     <a href="https://github.com/google/A2A/blob/main/docs/specification.md#8-error-handling"</a>
 * </p>
 *
 */
public class JSONParseError extends JsonRpcError {
    public JSONParseError() {
        this.setCode(-32700);
        this.setMessage("Invalid JSON payload");
    }
}


