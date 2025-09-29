package com.zhouruojun.a2acore.spec.message;

import com.zhouruojun.a2acore.spec.TaskSendParams;
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
public class SendTaskRequest extends JsonRpcRequest<TaskSendParams> {
    public SendTaskRequest() {
        this.setMethod("tasks/send");
    }

    public SendTaskRequest(TaskSendParams params) {
        this();
        this.setParams(params);
    }
}


