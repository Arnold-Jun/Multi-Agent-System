package com.zhouruojun.agentcore.client.sse;

import com.zhouruojun.agentcore.client.A2AClient;
import com.zhouruojun.agentcore.spec.message.SendTaskStreamingResponse;


public interface SseEventHandler {

    void onEvent(A2AClient a2AClient, SendTaskStreamingResponse sendTaskStreamingResponse);

    void onError(A2AClient a2AClient, Throwable throwable);

}
