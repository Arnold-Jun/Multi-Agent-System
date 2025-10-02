package com.zhouruojun.a2acore.spec.message;

import com.zhouruojun.a2acore.spec.Task;
import com.zhouruojun.a2acore.spec.error.JsonRpcError;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * <p>
 *     tasks/send
 *     <a href="https://github.com/google/A2A/blob/main/docs/specification.md#71-taskssend">µã»÷Ìø×ª</a>
 * </p>
 *
 */
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@Data
public class SendTaskResponse extends JsonRpcResponse<Task> {

    public SendTaskResponse() {
    }

    public SendTaskResponse(Task task) {
        this.setResult(task);
    }

    public SendTaskResponse(String id, JsonRpcError error) {
        this.setId(id);
        this.setError(error);
    }

    public SendTaskResponse(String id, JsonRpcError error, Task task) {
        this.setId(id);
        this.setError(error);
        this.setResult(task);
    }

}


