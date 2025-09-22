package com.zhouruojun.agentcore.session;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 会话管理器 - 简化版本
 */
@Slf4j
@Component
public class SessionManager {

    private static final SessionManager INSTANCE = new SessionManager();
    
    // 会话缓存
    private final Map<String, Object> sessionCache = new ConcurrentHashMap<>();

    private SessionManager() {}

    public static SessionManager getInstance() {
        return INSTANCE;
    }

    /**
     * 添加会话
     */
    public void addSession(String sessionId, Object sessionData) {
        sessionCache.put(sessionId, sessionData);
        log.info("Added session: {}", sessionId);
    }

    /**
     * 移除会话
     */
    public void removeSession(String sessionId) {
        sessionCache.remove(sessionId);
        log.info("Removed session: {}", sessionId);
    }

    /**
     * 获取会话数据
     */
    public Object getSession(String sessionId) {
        return sessionCache.get(sessionId);
    }

    /**
     * 发送消息到会话
     */
    public void sendMessage(String sessionId, String message) {
        Object sessionData = sessionCache.get(sessionId);
        if (sessionData != null) {
            log.info("Sending message to session {}: {}", sessionId, message);
            // 这里可以实现实际的消息发送逻辑
        } else {
            log.warn("Session not found: {}", sessionId);
        }
    }

    /**
     * 获取所有会话
     */
    public Map<String, Object> getAllSessions() {
        return new ConcurrentHashMap<>(sessionCache);
    }
}
