package com.zhouruojun.a2acore.client.sse;

import com.zhouruojun.a2acore.client.A2AClient;
import com.zhouruojun.a2acore.spec.message.SendTaskStreamingResponse;


public interface SseEventHandler {

    void onEvent(A2AClient a2AClient, SendTaskStreamingResponse sendTaskStreamingResponse);

    void onError(A2AClient a2AClient, Throwable throwable);

}


