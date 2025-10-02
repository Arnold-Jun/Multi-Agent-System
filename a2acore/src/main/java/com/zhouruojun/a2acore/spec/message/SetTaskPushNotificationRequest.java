package com.zhouruojun.a2acore.spec.message;

import com.zhouruojun.a2acore.spec.TaskPushNotificationConfig;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * <p>
 *     tasks/pushNotification/set
 *     <a href="https://github.com/google/A2A/blob/main/docs/specification.md#75-taskspushnotificationset">µã»÷Ìø×ª</a>
 * </p>
 *
 */
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@Data
public class SetTaskPushNotificationRequest extends JsonRpcRequest<TaskPushNotificationConfig> {
    public SetTaskPushNotificationRequest() {
        this.setMethod("tasks/pushNotification/set");
    }

    public SetTaskPushNotificationRequest(TaskPushNotificationConfig params) {
        this();
        this.setParams(params);
    }
}


