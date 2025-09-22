package com.zhouruojun.agentcore.spec.error;

/**
 * <p>
 *     <a href="https://github.com/google/A2A/blob/main/docs/specification.md#8-error-handling">点击跳转</a>
 * </p>
 *
 */
public class TaskNotFoundError extends JsonRpcError {
    public TaskNotFoundError() {
        this.setCode(-32001);
        this.setMessage("Task not found");
    }
}
