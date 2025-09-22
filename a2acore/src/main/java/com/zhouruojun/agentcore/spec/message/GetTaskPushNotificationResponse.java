package com.zhouruojun.agentcore.spec.message;

import com.zhouruojun.agentcore.spec.TaskPushNotificationConfig;
import com.zhouruojun.agentcore.spec.error.JsonRpcError;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * <p>
 *     tasks/pushNotification/get
 *     <a href="https://github.com/google/A2A/blob/main/docs/specification.md#76-taskspushnotificationget">点击跳转</a>
 * </p>
 *
 */
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@Data
public class GetTaskPushNotificationResponse extends JsonRpcResponse<TaskPushNotificationConfig> {
    public GetTaskPushNotificationResponse() {
    }

    public GetTaskPushNotificationResponse(String id, JsonRpcError error) {
        this.setId(id);
        this.setError(error);
    }

    public GetTaskPushNotificationResponse(String id, TaskPushNotificationConfig taskPushNotificationConfig) {
        this.setId(id);
        this.setResult(taskPushNotificationConfig);
    }
}
