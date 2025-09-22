package com.zhouruojun.agentcore.spec.message;

import com.zhouruojun.agentcore.spec.Task;
import com.zhouruojun.agentcore.spec.error.TaskNotFoundError;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * <p>
 *     tasks/get
 *     <a href="https://github.com/google/A2A/blob/main/docs/specification.md#73-tasksget">点击跳转</a>
 * </p>
 *
 */
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@Data
public class GetTaskResponse extends JsonRpcResponse<Task> {
    public GetTaskResponse() {
    }

    public GetTaskResponse(String id, TaskNotFoundError taskNotFoundError) {
        this.setId(id);
        this.setError(taskNotFoundError);
    }

    public GetTaskResponse(String id, Task taskResult) {
        this.setId(id);
        this.setResult(taskResult);
    }
}
