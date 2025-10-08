package com.zhouruojun.a2acore.spec.message;

import com.zhouruojun.a2acore.spec.Task;
import com.zhouruojun.a2acore.spec.error.TaskNotFoundError;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * <p>
 *     tasks/get
 *     <a href="https://github.com/google/A2A/blob/main/docs/specification.md#73-tasksget"</a>
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


