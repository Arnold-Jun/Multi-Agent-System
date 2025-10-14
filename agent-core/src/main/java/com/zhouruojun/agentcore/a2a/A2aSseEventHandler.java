package com.zhouruojun.agentcore.a2a;

import com.alibaba.fastjson.JSONObject;
import com.zhouruojun.a2acore.client.A2AClient;
import com.zhouruojun.a2acore.client.sse.SseEventHandler;
import com.zhouruojun.a2acore.spec.UpdateEvent;
import com.zhouruojun.a2acore.spec.message.SendTaskStreamingResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
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
        // 使用更保守的心跳策略：每3分钟发送一次心跳，初始延迟2分钟
        heartbeatScheduler.scheduleAtFixedRate(this::sendHeartbeat, 120, 180, TimeUnit.SECONDS);
        // 添加连接健康检查：每5分钟检查一次连接状态，初始延迟5分钟
        heartbeatScheduler.scheduleAtFixedRate(this::checkConnectionHealth, 300, 300, TimeUnit.SECONDS);
        log.info("SSE heartbeat mechanism enabled with very conservative settings (120s initial delay, 180s interval)");
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
        if (emitter == null) {
            log.warn("Cannot register null SSE emitter for session: {}", sessionId);
            return;
        }
        
        // 清理可能存在的旧连接
        SseEmitter oldEmitter = frontendConnections.remove(sessionId);
        if (oldEmitter != null) {
            try {
                oldEmitter.complete();
                log.debug("Completed old SSE connection for session: {}", sessionId);
            } catch (Exception e) {
                log.debug("Error completing old SSE connection for session: {} - {}", sessionId, e.getMessage());
            }
        }
        
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
            log.debug("Frontend SSE connection error for session: {} - {}", sessionId, throwable.getMessage());
            frontendConnections.remove(sessionId);
        });
        
        // 注册新连接
        frontendConnections.put(sessionId, emitter);
        log.info("Registered frontend SSE connection for session: {} (total connections: {})", 
            sessionId, frontendConnections.size());
        
        // 发送初始连接确认消息
        try {
            emitter.send(SseEmitter.event()
                .name("connection")
                .data(Map.of(
                    "type", "connection",
                    "message", "SSE连接已建立",
                    "sessionId", sessionId,
                    "timestamp", System.currentTimeMillis()
                )));
            log.debug("Sent initial connection confirmation to session: {}", sessionId);
        } catch (Exception e) {
            log.debug("Failed to send initial connection confirmation to session: {} - {}", sessionId, e.getMessage());
            // 如果初始消息发送失败，移除连接
            frontendConnections.remove(sessionId);
        }
    }
    
    /**
     * 向前端推送消息
     * @return true if successfully pushed, false otherwise
     */
    public boolean pushToFrontend(String sessionId, String message, String type) {
        SseEmitter emitter = frontendConnections.get(sessionId);
        if (emitter != null) {
            try {
                // 使用增强的连接状态检查
                if (isConnectionClosed(emitter)) {
                    log.debug("SSE connection already closed for session: {}", sessionId);
                    frontendConnections.remove(sessionId);
                    return false;
                }
                
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
                return true;
            } catch (IllegalStateException e) {
                log.debug("SSE connection completed/expired for session: {} - {}", sessionId, e.getMessage());
                frontendConnections.remove(sessionId);
                return false;
            } catch (IOException e) {
                log.debug("SSE connection disconnected for session: {} - {}", sessionId, e.getMessage());
                frontendConnections.remove(sessionId);
                return false;
            } catch (Exception e) {
                log.debug("Failed to push message to frontend for session: {} - {}", sessionId, e.getMessage());
                frontendConnections.remove(sessionId);
                return false;
            }
        } else {
            log.debug("No SSE connection found for session: {}", sessionId);
            return false;
        }
    }
    
    /**
     * 推送异步结果到前端
     */
    public void pushAsyncResult(String sessionId, String result) {
        log.info("Pushing async result for session {}: {}", sessionId, result.length() > 100 ? result.substring(0, 100) + "..." : result);
        
        // 尝试通过SSE推送
        boolean sseSuccess = pushToFrontend(sessionId, result, "async_result");
        
        if (!sseSuccess) {
            log.warn("SSE push failed for session {}, result will be available via polling", sessionId);
        }
    }
    
    /**
     * 发送心跳消息
     */
    private void sendHeartbeat() {
        if (frontendConnections.isEmpty()) {
            return; // 没有连接时直接返回
        }
        
        frontendConnections.entrySet().removeIf(entry -> {
            String sessionId = entry.getKey();
            SseEmitter emitter = entry.getValue();
            
            try {
                if (emitter == null) {
                    log.debug("Removing null SSE connection for session: {}", sessionId);
                    return true;
                }
                
                // 使用反射检查连接状态，避免向已断开的连接发送数据
                if (isConnectionClosed(emitter)) {
                    log.debug("Removing closed SSE connection for session: {}", sessionId);
                    return true;
                }
                
                // 尝试发送心跳，使用更安全的方式
                try {
                    // 再次检查连接状态，确保在发送前连接仍然有效
                    if (isConnectionClosed(emitter)) {
                        log.debug("Connection closed during heartbeat send for session: {}", sessionId);
                        return true; // 移除连接
                    }
                    
                    emitter.send(SseEmitter.event()
                        .name("heartbeat")
                        .data(Map.of(
                            "type", "heartbeat",
                            "timestamp", System.currentTimeMillis(),
                            "message", "连接正常"
                        )));
                    
                    log.debug("Heartbeat sent successfully to session: {}", sessionId);
                } catch (Exception sendException) {
                    // 如果发送失败，立即抛出异常让外层catch处理
                    log.debug("Failed to send heartbeat to session {}: {}", sessionId, sendException.getMessage());
                    throw sendException;
                }
                
                return false; // 连接正常，不移除
                
            } catch (IllegalStateException e) {
                // 连接已完成或超时
                log.debug("Removing completed/expired SSE connection for session: {} - {}", 
                    sessionId, e.getMessage());
                return true;
            } catch (IOException e) {
                // 连接已断开 - 这是最常见的情况
                log.debug("Removing disconnected SSE connection for session: {} - {}", 
                    sessionId, e.getMessage());
                return true;
            } catch (Exception e) {
                // 其他异常
                log.debug("Error sending heartbeat to session: {} - {}", 
                    sessionId, e.getMessage());
                return true; // 出错时移除连接
            }
        });
    }
    
    /**
     * 检查SSE连接是否已关闭
     * 使用反射来检查内部状态，避免向已断开的连接发送数据
     */
    private boolean isConnectionClosed(SseEmitter emitter) {
        try {
            // 检查timeout是否为0（表示连接已完成）
            if (emitter.getTimeout() == 0) {
                return true;
            }
            
            // 使用反射检查ResponseBodyEmitter的内部状态
            java.lang.reflect.Field responseField = emitter.getClass().getSuperclass().getDeclaredField("response");
            responseField.setAccessible(true);
            Object response = responseField.get(emitter);
            
            if (response != null) {
                // 检查response是否已完成
                java.lang.reflect.Method isCommittedMethod = response.getClass().getMethod("isCommitted");
                isCommittedMethod.setAccessible(true);
                Boolean isCommitted = (Boolean) isCommittedMethod.invoke(response);
                
                if (Boolean.TRUE.equals(isCommitted)) {
                    return true;
                }
            }
            
            return false;
        } catch (Exception e) {
            // 反射失败时，保守地认为连接可能已关闭
            log.debug("Failed to check SSE connection status: {}", e.getMessage());
            return true; // 更保守的策略：如果无法检查，认为连接已关闭
        }
    }
    
    /**
     * 清理所有前端连接
     */
    public void cleanupAllConnections() {
        log.info("Cleaning up all SSE connections, count: {}", frontendConnections.size());
        frontendConnections.entrySet().removeIf(entry -> {
            try {
                entry.getValue().complete();
                log.debug("Completed SSE connection for session: {}", entry.getKey());
                return true;
            } catch (Exception e) {
                log.debug("Error completing SSE connection for session: {} - {}", 
                    entry.getKey(), e.getMessage());
                return true;
            }
        });
    }
    
    /**
     * 获取活跃连接数量
     */
    public int getActiveConnectionCount() {
        return frontendConnections.size();
    }
    
    /**
     * 手动发送心跳（可选功能）
     */
    public void sendHeartbeatManually() {
        if (!frontendConnections.isEmpty()) {
            log.debug("Manually sending heartbeat to {} connections", frontendConnections.size());
            sendHeartbeat();
        }
    }
    
    /**
     * 检查连接健康状态
     */
    private void checkConnectionHealth() {
        if (frontendConnections.isEmpty()) {
            return;
        }
        
        log.debug("Checking health of {} SSE connections", frontendConnections.size());
        
        frontendConnections.entrySet().removeIf(entry -> {
            String sessionId = entry.getKey();
            SseEmitter emitter = entry.getValue();
            
            if (emitter == null) {
                log.debug("Removing null SSE connection for session: {}", sessionId);
                return true;
            }
            
            if (isConnectionClosed(emitter)) {
                log.debug("Removing unhealthy SSE connection for session: {}", sessionId);
                return true;
            }
            
            return false;
        });
        
        log.debug("Connection health check completed, {} connections remain", frontendConnections.size());
    }
    
    /**
     * 关闭心跳调度器
     */
    public void shutdown() {
        log.info("Shutting down SSE heartbeat scheduler");
        heartbeatScheduler.shutdown();
        try {
            if (!heartbeatScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                heartbeatScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            heartbeatScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
