package com.zhouruojun.agentcore.a2a;

import com.alibaba.fastjson.JSONObject;
import com.zhouruojun.a2acore.client.A2AClient;
import com.zhouruojun.a2acore.client.sse.SseEventHandler;
import com.zhouruojun.a2acore.spec.UpdateEvent;
import com.zhouruojun.a2acore.spec.message.SendTaskStreamingResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * A2A SSE事件处理器
 * 用于处理A2A的task/subscribe接口的后续监听逻辑
 */
@Slf4j
@Component
public class A2aSseEventHandler implements SseEventHandler {

    // 事件回调处理器
    private Consumer<UpdateEvent> eventCallback;
    
    // 存储活跃的前端SSE连接
    private final Map<String, SseEmitter> frontendConnections = new ConcurrentHashMap<>();
    
    // 心跳调度器
    private final ScheduledExecutorService heartbeatScheduler = Executors.newScheduledThreadPool(1);
    
    public A2aSseEventHandler() {
        // 每30秒发送一次心跳
        heartbeatScheduler.scheduleAtFixedRate(this::sendHeartbeat, 30, 30, TimeUnit.SECONDS);
    }

    /**
     * 设置事件回调处理器
     * @param callback 事件回调函数
     */
    public void setEventCallback(Consumer<UpdateEvent> callback) {
        this.eventCallback = callback;
    }

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
        if (eventCallback != null) {
            eventCallback.accept(result);
        } else {
            log.warn("Event callback is not set, skipping event processing");
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
    
    /**
     * 注册前端SSE连接
     */
    public void registerFrontendConnection(String sessionId, SseEmitter emitter) {
        frontendConnections.put(sessionId, emitter);
        log.info("Registered frontend SSE connection for session: {}", sessionId);
        
        // 设置连接完成和错误处理
        emitter.onCompletion(() -> {
            log.info("Frontend SSE connection completed for session: {}", sessionId);
            frontendConnections.remove(sessionId);
        });
        
        emitter.onTimeout(() -> {
            log.warn("Frontend SSE connection timeout for session: {}", sessionId);
            frontendConnections.remove(sessionId);
        });
        
        emitter.onError(throwable -> {
            log.error("Frontend SSE connection error for session: {}", sessionId, throwable);
            frontendConnections.remove(sessionId);
        });
    }
    
    /**
     * 向前端推送消息
     */
    public void pushToFrontend(String sessionId, String message, String type) {
        SseEmitter emitter = frontendConnections.get(sessionId);
        if (emitter != null) {
            try {
                Map<String, Object> data = Map.of(
                    "type", type,
                    "message", message,
                    "sessionId", sessionId,
                    "timestamp", System.currentTimeMillis()
                );
                
                emitter.send(SseEmitter.event()
                    .name(type)
                    .data(data));
                
                log.info("Pushed message to frontend for session {}: {}", sessionId, type);
            } catch (Exception e) {
                log.error("Failed to push message to frontend for session: {}", sessionId, e);
                frontendConnections.remove(sessionId);
            }
        }
    }
    
    /**
     * 推送异步结果到前端
     */
    public void pushAsyncResult(String sessionId, String result) {
        pushToFrontend(sessionId, result, "async_result");
    }
    
    /**
     * 发送心跳消息
     */
    private void sendHeartbeat() {
        frontendConnections.entrySet().removeIf(entry -> {
            try {
                entry.getValue().send(SseEmitter.event()
                    .name("heartbeat")
                    .data(Map.of(
                        "type", "heartbeat",
                        "timestamp", System.currentTimeMillis(),
                        "message", "连接正常"
                    )));
                return false; // 连接正常，不移除
            } catch (Exception e) {
                log.debug("Removing inactive SSE connection for session: {}", entry.getKey());
                return true; // 连接已断开，移除
            }
        });
    }
}
