package com.zhouruojun.mcpserver.mcp;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * MCP会话清理任务
 * 定期清理过期的MCP会话
 */
@Slf4j
@Component
public class MCPSessionCleanupTask {

    @Autowired
    private MCPSessionManager sessionManager;

    /**
     * 每5分钟清理一次过期会话
     */
    @Scheduled(fixedRate = 300000) // 5分钟
    public void cleanupExpiredSessions() {
        try {
            sessionManager.cleanupExpiredSessions();
        } catch (Exception e) {
            log.error("❌ 会话清理任务执行失败", e);
        }
    }
}
