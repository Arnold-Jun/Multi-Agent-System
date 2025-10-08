package com.xiaohongshu.codewiz.codewizagent.agentcore.a2a;

import com.xiaohongshu.codewiz.a2acore.spec.PushNotificationConfig;
import lombok.Data;

/**
 * <p>
 * a2a Agent注册
 * </p>
 *
 * @author 瑞诺
 * create on 2025/5/26 21:00
 */
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

    // maybe null
    private PushNotificationConfig notification;
}
