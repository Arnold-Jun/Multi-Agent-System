package com.zhouruojun.agentcore.spec;

import java.io.Serializable;
import lombok.Builder;
import lombok.Data;
import lombok.ToString;

/**
 * <p>
 * 任务推送的消息通知配置
 * <a href="https://github.com/google/A2A/blob/main/docs/specification.md#610-taskpushnotificationconfig-object">点击跳转</a>
 * </p>
 *
 */
@ToString
@Builder
@Data
public class TaskPushNotificationConfig implements Serializable {
    /**
     * task id
     */
    private String id;
    private PushNotificationConfig pushNotificationConfig;
}
