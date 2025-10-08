package com.zhouruojun.a2acore.spec.message;

import com.zhouruojun.a2acore.spec.TaskSendParams;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * <p>
 *     tasks/sendSubscribe
 *     <a href="https://github.com/google/A2A/blob/main/docs/specification.md#72-taskssendsubscribe"></a>
 * </p>
 */
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@Data
public class SendTaskStreamingRequest extends JsonRpcRequest<TaskSendParams> {
    public SendTaskStreamingRequest() {
        this.setMethod("tasks/sendSubscribe");
    }

    public SendTaskStreamingRequest(TaskSendParams params) {
        this();
        this.setParams(params);
    }
}


