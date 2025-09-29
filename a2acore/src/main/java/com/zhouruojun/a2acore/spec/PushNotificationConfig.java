package com.zhouruojun.a2acore.spec;

import java.io.Serializable;
import lombok.Data;
import lombok.ToString;

/**
 * <p>
 * ������Ϣ��?? * <a href="https://github.com/google/A2A/blob/main/docs/specification.md#68-pushnotificationconfig-object">�����ת</a>
 * <a href="https://google.github.io/A2A/specification/agent-to-agent-communication/#push-notifications">�ĵ�����</a>
 * </p>
 *
 */
@ToString
@Data
public class PushNotificationConfig implements Serializable {
    private String url;
    @Nullable
    private String token;
    @Nullable
    private AuthenticationInfo authentication;
}


