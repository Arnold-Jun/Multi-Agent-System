package com.zhouruojun.agentcore.spec.message;

import com.zhouruojun.agentcore.spec.TaskPushNotificationConfig;
import com.zhouruojun.agentcore.spec.error.JsonRpcError;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * <p>
 *     tasks/pushNotification/set
 *     <a href="https://github.com/google/A2A/blob/main/docs/specification.md#75-taskspushnotificationset">点击跳转</a>
 * </p>
 *
 */
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@Data
public class SetTaskPushNotificationResponse extends JsonRpcResponse<TaskPushNotificationConfig> {
    public SetTaskPushNotificationResponse() {
    }

    public SetTaskPushNotificationResponse(String id, JsonRpcError error) {
        this.setId(id);
        this.setError(error);
    }

    public SetTaskPushNotificationResponse(String id, TaskPushNotificationConfig taskPushNotificationConfig) {
        this.setId(id);
        this.setResult(taskPushNotificationConfig);
    }
}
