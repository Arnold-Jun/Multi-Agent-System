package com.zhouruojun.a2acore.spec;

import java.io.Serializable;
import lombok.Builder;
import lombok.Data;
import lombok.ToString;

/**
 * <p>
 * <a href="https://github.com/google/A2A/blob/main/docs/specification.md#610-taskpushnotificationconfig-object"></a>
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


