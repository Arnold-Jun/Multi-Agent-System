package com.zhouruojun.agentcore.spec.error;

/**
 * <p>
 *     <a href="https://github.com/google/A2A/blob/main/docs/specification.md#8-error-handling">点击跳转</a>
 * </p>
 *
 */
public class TaskNotCancelableError extends JsonRpcError {
    public TaskNotCancelableError() {
        this.setCode(-32002);
        this.setMessage("Task cannot be canceled");
    }
}
