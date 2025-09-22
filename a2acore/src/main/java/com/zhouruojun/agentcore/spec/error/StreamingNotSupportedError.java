package com.zhouruojun.agentcore.spec.error;

/**
 * <p>
 *     <a href="https://github.com/google/A2A/blob/main/docs/specification.md#8-error-handling">点击跳转</a>
 * </p>
 *
 */
public class StreamingNotSupportedError extends JsonRpcError {
    public StreamingNotSupportedError() {
        this.setCode(-32006);
        this.setMessage("Streaming is not supported");
    }
}
