package com.zhouruojun.a2acore.spec.message;

import com.zhouruojun.a2acore.spec.TaskQueryParams;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * <p>
 *     tasks/get
 *     <a href="https://github.com/google/A2A/blob/main/docs/specification.md#73-tasksget"></a>
 * </p>
 *
 */
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@Data
public class GetTaskRequest extends JsonRpcRequest<TaskQueryParams> {
    public GetTaskRequest() {
        this.setMethod("tasks/get");
    }

    public GetTaskRequest(TaskQueryParams params) {
        this();
        this.setParams(params);
    }
}


