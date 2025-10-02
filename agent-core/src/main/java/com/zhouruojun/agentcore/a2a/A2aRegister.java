package com.zhouruojun.agentcore.a2a;

import com.zhouruojun.a2acore.spec.PushNotificationConfig;
import lombok.Data;

/**
 * A2A智能体注册信息
 */
@Data
public class A2aRegister {
    private String name;
    private String baseUrl;
    private String agentCardPath = "/.well-known/agent.json";
    private PushNotificationConfig notification;
}
