package com.zhouruojun.agentcore.a2a;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A2A SSE事件处理器
 * 处理服务器发送事件(Server-Sent Events)
 */
@Component
@Slf4j
public class A2aSseEventHandler {

    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    /**
     * 创建SSE连接
     */
    public SseEmitter createConnection(String sessionId) {
        SseEmitter emitter = new SseEmitter(30000L); // 30秒超时
        emitters.put(sessionId, emitter);
        
        emitter.onCompletion(() -> {
            log.info("SSE connection completed for session: {}", sessionId);
            emitters.remove(sessionId);
        });
        
        emitter.onTimeout(() -> {
            log.warn("SSE connection timeout for session: {}", sessionId);
            emitters.remove(sessionId);
        });
        
        emitter.onError(throwable -> {
            log.error("SSE connection error for session: {}", sessionId, throwable);
            emitters.remove(sessionId);
        });
        
        log.info("Created SSE connection for session: {}", sessionId);
        return emitter;
    }

    /**
     * 发送消息到指定会话
     */
    public void sendMessage(String sessionId, String message) {
        SseEmitter emitter = emitters.get(sessionId);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event()
                        .name("message")
                        .data(message));
                log.debug("Sent message to session {}: {}", sessionId, message);
            } catch (IOException e) {
                log.error("Failed to send message to session: {}", sessionId, e);
                emitters.remove(sessionId);
            }
        } else {
            log.warn("No SSE connection found for session: {}", sessionId);
        }
    }

    /**
     * 发送事件到指定会话
     */
    public void sendEvent(String sessionId, String eventName, Object data) {
        SseEmitter emitter = emitters.get(sessionId);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event()
                        .name(eventName)
                        .data(data));
                log.debug("Sent event {} to session {}: {}", eventName, sessionId, data);
            } catch (IOException e) {
                log.error("Failed to send event to session: {}", sessionId, e);
                emitters.remove(sessionId);
            }
        } else {
            log.warn("No SSE connection found for session: {}", sessionId);
        }
    }

    /**
     * 关闭指定会话的连接
     */
    public void closeConnection(String sessionId) {
        SseEmitter emitter = emitters.remove(sessionId);
        if (emitter != null) {
            try {
                emitter.complete();
                log.info("Closed SSE connection for session: {}", sessionId);
            } catch (Exception e) {
                log.error("Error closing SSE connection for session: {}", sessionId, e);
            }
        }
    }

    /**
     * 获取活跃连接数
     */
    public int getActiveConnectionCount() {
        return emitters.size();
    }

    /**
     * 获取所有活跃会话ID
     */
    public java.util.Set<String> getActiveSessionIds() {
        return emitters.keySet();
    }
}
