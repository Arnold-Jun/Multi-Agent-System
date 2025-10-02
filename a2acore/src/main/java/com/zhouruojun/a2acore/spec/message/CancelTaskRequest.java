package com.zhouruojun.a2acore.spec.message;

import com.zhouruojun.a2acore.spec.TaskIdParams;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * <p>
 *     tasks/cancel
 *     <a href="https://github.com/google/A2A/blob/main/docs/specification.md#74-taskscancel">µã»÷Ìø×ª</a>
 * </p>
 */
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@Data
public class CancelTaskRequest extends JsonRpcRequest<TaskIdParams> {
    public CancelTaskRequest() {
        this.setMethod("tasks/cancel");
    }

    public CancelTaskRequest(TaskIdParams params) {
        this();
        this.setParams(params);
    }
}


