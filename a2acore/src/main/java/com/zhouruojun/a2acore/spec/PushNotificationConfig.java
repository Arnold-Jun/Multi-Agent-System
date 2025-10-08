package com.zhouruojun.a2acore.spec;

import java.io.Serializable;
import lombok.Data;
import lombok.ToString;

/**
 * <p>
 * "https://github.com/google/A2A/blob/main/docs/specification.md#68-pushnotificationconfig-object"</a>
 * <a href="https://google.github.io/A2A/specification/agent-to-agent-communication/#push-notifications">
 * </p>
 *
 */
@ToString
@Data
public class PushNotificationConfig implements Serializable {
    private String url;
    private String token;
    private AuthenticationInfo authentication;
}


