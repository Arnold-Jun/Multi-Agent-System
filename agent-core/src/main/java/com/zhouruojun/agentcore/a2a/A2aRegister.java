package com.zhouruojun.agentcore.a2a;

import com.zhouruojun.agentcore.spec.PushNotificationConfig;
import lombok.Data;

/**
 * A2A Agent注册信息
 *
 * */
@Data
public class A2aRegister {
    /**
     * agent名称
     */
    private String name;
    
    /**
     * agent服务地址
     */
    private String baseUrl;
    
    /**
     * agent的卡片注册地址
     * 默认 /.well-known/agent.json
     */
    private String agentCardPath;

    /**
     * 推送通知配置，可能为null
     */
    private PushNotificationConfig notification;
}




