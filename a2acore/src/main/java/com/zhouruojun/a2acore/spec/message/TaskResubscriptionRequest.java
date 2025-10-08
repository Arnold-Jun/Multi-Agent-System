package com.zhouruojun.a2acore.spec.message;

import com.zhouruojun.a2acore.spec.TaskIdParams;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * <p>
 *    tasks/resubscribe
 *    <a href="https://github.com/google/A2A/blob/main/docs/specification.md#77-tasksresubscribe"></a>
 * </p>
 *
 */
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@Data
public class TaskResubscriptionRequest extends JsonRpcRequest<TaskIdParams> {
    public TaskResubscriptionRequest() {
        this.setMethod("tasks/resubscribe");
    }

    public TaskResubscriptionRequest(TaskIdParams params) {
        this();
        this.setParams(params);
    }
}


