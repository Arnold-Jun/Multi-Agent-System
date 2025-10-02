package com.zhouruojun.a2acore.spec;

import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * <p>
 * 任务发送请求，用于tasks/send场景
 * <a href="https://github.com/google/A2A/blob/main/docs/specification.md#711-tasksendparams-object">点击跳转</a>
 * </p>
 *
 */
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class TaskSendParams {
    /**
     * task id
     */
    private String id;
    // uuid 4 hex
    private String sessionId;
    private Message message;
    @Nullable
    private List<String> acceptedOutputModes;
    @Nullable
    private PushNotificationConfig pushNotification;
    @Nullable
    private Integer historyLength;
    @Nullable
    private Map<String, Object> metadata;

}


