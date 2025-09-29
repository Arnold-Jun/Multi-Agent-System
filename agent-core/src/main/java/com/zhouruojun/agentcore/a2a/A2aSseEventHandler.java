package com.zhouruojun.agentcore.a2a;

import com.alibaba.fastjson.JSONObject;
import com.zhouruojun.a2acore.client.A2AClient;
import com.zhouruojun.a2acore.client.sse.SseEventHandler;
import com.zhouruojun.a2acore.spec.UpdateEvent;
import com.zhouruojun.a2acore.spec.message.SendTaskStreamingResponse;
import com.zhouruojun.agentcore.agent.core.AgentControllerCore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * A2A SSE事件处理器 - 基于codewiz经验重构
 * 用于处理A2A的task/subscribe接口的后续监听逻辑
 */
@Slf4j
@Component
public class A2aSseEventHandler implements SseEventHandler {

    @Autowired(required = false)
    private AgentControllerCore agentControllerCore;

    /**
     * 处理event消息
     * 由于是sse消息，用于交互链路。
     * @param a2AClient A2A客户端
     * @param sendTaskStreamingResponse 流式响应
     */
    @Override
    public void onEvent(A2AClient a2AClient, SendTaskStreamingResponse sendTaskStreamingResponse) {
        log.info("receive task streaming response: {}", JSONObject.toJSONString(sendTaskStreamingResponse));
        UpdateEvent result = sendTaskStreamingResponse.getResult();
        if (agentControllerCore != null) {
            agentControllerCore.a2aAiMessageOnEvent(result);
        } else {
            log.warn("AgentControllerCore is not available, skipping event processing");
        }
    }

    /**
     * 处理异常消息
     * @param a2AClient A2A客户端
     * @param throwable 异常
     */
    @Override
    public void onError(A2AClient a2AClient, Throwable throwable) {
        log.error("a2a client:{}, errorMessage:{}", a2AClient.getAgentCard().getName(), throwable.getMessage(), throwable);
        // 可以在这里添加异常处理逻辑，比如重试、通知等
    }
}
