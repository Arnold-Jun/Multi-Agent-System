package com.zhouruojun.agentcore.spec.error;

/**
 * <p>
 *     <a href="https://github.com/google/A2A/blob/main/docs/specification.md#8-error-handling">点击跳转</a>
 * </p>
 *
 */
public class InvalidParamsError extends JsonRpcError {
    public InvalidParamsError() {
        this.setCode(-32602);
        this.setMessage("Invalid parameters");
    }

    public InvalidParamsError(String message) {
        this();
        this.setMessage(message);
    }
}
