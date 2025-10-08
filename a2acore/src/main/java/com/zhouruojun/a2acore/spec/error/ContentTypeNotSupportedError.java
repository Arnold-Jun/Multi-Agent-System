package com.zhouruojun.a2acore.spec.error;

/**
 * <p>
 * <a href="https://github.com/google/A2A/blob/main/docs/specification.md#8-error-handling"></a>
 * </p>
 *
 */
public class ContentTypeNotSupportedError extends JsonRpcError {
    public ContentTypeNotSupportedError() {
        this.setCode(-32005);
        this.setMessage("Incompatible content types");
    }
}


