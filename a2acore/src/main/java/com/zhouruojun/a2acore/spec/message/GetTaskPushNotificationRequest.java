package com.zhouruojun.a2acore.spec.message;

import com.zhouruojun.a2acore.spec.TaskIdParams;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * <p>
 *     tasks/pushNotification/get
 *     <a href="https://github.com/google/A2A/blob/main/docs/specification.md#76-taskspushnotificationget"></a>
 * </p>
 *
 */
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@Data
public class GetTaskPushNotificationRequest extends JsonRpcRequest<TaskIdParams> {
    public GetTaskPushNotificationRequest() {
        this.setMethod("tasks/pushNotification/get");
    }

    public GetTaskPushNotificationRequest(TaskIdParams params) {
        this();
        this.setParams(params);
    }
}


