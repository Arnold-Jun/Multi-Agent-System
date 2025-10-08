package com.zhouruojun.a2acore.spec;

import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;


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


