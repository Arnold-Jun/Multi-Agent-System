package com.zhouruojun.a2a.client.autoconfiguration;

import com.zhouruojun.a2acore.spec.PushNotificationConfig;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * <p>
 * a2a client properties
 * </p>
 *
 */
@Data
@ConfigurationProperties(prefix = "a2a.host")
public class A2aAgentsProperties {
    /**
     * host global push notification config
     */
    private PushNotificationConfig notification;

    /**
     * remote agents config
     */
    private Map<String, A2aAgentProperties> agents;
}
