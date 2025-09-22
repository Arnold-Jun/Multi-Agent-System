package com.zhouruojun.agentcore.spec.error;

/**
 * <p>
 * <a href="https://github.com/google/A2A/blob/main/docs/specification.md#8-error-handling">点击跳转</a>
 * </p>
 *
 */
public class ContentTypeNotSupportedError extends JsonRpcError {
    public ContentTypeNotSupportedError() {
        this.setCode(-32005);
        this.setMessage("Incompatible content types");
    }
}
