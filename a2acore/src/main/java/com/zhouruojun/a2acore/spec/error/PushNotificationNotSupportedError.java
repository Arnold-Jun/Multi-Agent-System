package com.zhouruojun.a2acore.spec.error;

/**
 * <p>
 *     <a href="https://github.com/google/A2A/blob/main/docs/specification.md#8-error-handling"></a>
 * </p>
 *
 */
public class PushNotificationNotSupportedError extends JsonRpcError {
    public PushNotificationNotSupportedError() {
        this.setCode(-32003);
        this.setMessage("Push Notification is not supported");
    }
}


