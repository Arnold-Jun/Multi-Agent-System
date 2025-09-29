package com.zhouruojun.a2acore.spec.message;

import com.zhouruojun.a2acore.spec.Task;
import com.zhouruojun.a2acore.spec.error.JsonRpcError;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * <p>
 *     tasks/cancel
 *     <a href="https://github.com/google/A2A/blob/main/docs/specification.md#74-taskscancel">µã»÷Ìø×ª</a>
 * </p>
 *
 */
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@Data
public class CancelTaskResponse extends JsonRpcResponse<Task> {
    public CancelTaskResponse(String id, JsonRpcError error) {
        this.setId(id);
        this.setError(error);
    }

    public CancelTaskResponse(String id, Task taskResult) {
        this.setId(id);
        this.setResult(taskResult);
    }
}


