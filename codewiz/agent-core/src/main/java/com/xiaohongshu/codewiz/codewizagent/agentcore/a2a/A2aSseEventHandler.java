package com.xiaohongshu.codewiz.codewizagent.agentcore.a2a;

import com.alibaba.fastjson.JSONObject;
import com.xiaohongshu.codewiz.a2acore.client.A2AClient;
import com.xiaohongshu.codewiz.a2acore.client.sse.SseEventHandler;
import com.xiaohongshu.codewiz.a2acore.spec.TaskArtifactUpdateEvent;
import com.xiaohongshu.codewiz.a2acore.spec.TaskStatusUpdateEvent;
import com.xiaohongshu.codewiz.a2acore.spec.UpdateEvent;
import com.xiaohongshu.codewiz.a2acore.spec.message.SendTaskStreamingResponse;
import com.xiaohongshu.codewiz.codewizagent.agentcore.agent.core.AgentControllerCore;
import javax.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * <p>
 * 用于处理a2a的 task/subscribe 接口的后续监听逻辑
 * </p>
 *
 * @author 瑞诺
 * create on 2025/5/14 10:58
 */
@Slf4j
@Component
public class A2aSseEventHandler implements SseEventHandler {

    @Resource
    private AgentControllerCore agentControllerCore;

    /**
     * 处理event消息
     * 由于是sse消息，用于交互链路。
     * @param a2AClient
     * @param sendTaskStreamingResponse
     */
    @Override
    public void onEvent(A2AClient a2AClient, SendTaskStreamingResponse sendTaskStreamingResponse) {
        log.info("receive task streaming response: {}", JSONObject.toJSONString(sendTaskStreamingResponse));
        UpdateEvent result = sendTaskStreamingResponse.getResult();
        agentControllerCore.a2aAiMessageOnEvent(result);
    }

    /**
     * 处理异常消息
     * @param a2AClient
     * @param throwable
     */
    @Override
    public void onError(A2AClient a2AClient, Throwable throwable) {
        log.error("a2a client:{}, errorMessage:{}", a2AClient.getAgentCard().getName(), throwable.getMessage(), throwable);
        // todo 异常消息处理
    }
}
