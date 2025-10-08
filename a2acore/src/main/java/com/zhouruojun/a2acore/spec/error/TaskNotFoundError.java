package com.zhouruojun.a2acore.spec.error;

/**
 * <p>
 *     <a href="https://github.com/google/A2A/blob/main/docs/specification.md#8-error-handling"></a>
 * </p>
 *
 */
public class TaskNotFoundError extends JsonRpcError {
    public TaskNotFoundError() {
        this.setCode(-32001);
        this.setMessage("Task not found");
    }
}


