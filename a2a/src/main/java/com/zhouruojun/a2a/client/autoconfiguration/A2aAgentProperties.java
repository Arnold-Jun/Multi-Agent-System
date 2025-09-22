package com.zhouruojun.a2a.client.autoconfiguration;


import com.zhouruojun.agentcore.spec.PushNotificationConfig;
import lombok.Data;

/**
 * <p>
 * a2a client properties
 * </p>
 *
 */
@Data
public class A2aAgentProperties {
    private String baseUrl;
    private String agentCardPath;

    // maybe null
    private PushNotificationConfig notification;
}
