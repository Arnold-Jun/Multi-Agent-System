package com.zhouruojun.mcpserver.mcp;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * MCP客户端
 * 连接到多个外部MCP服务器（高德地图、小红书等）
 * 使用统一的会话管理器处理不同MCP服务器的请求模式
 */
@Slf4j
@Component
public class MCPClient {

    private final MCPSessionManager sessionManager;

    @Autowired
    public MCPClient(MCPSessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    /**
     * 初始化MCP连接
     * 使用会话管理器统一处理
     */
    public CompletableFuture<Map<String, Object>> initialize(String serverType) {
        return sessionManager.getOrCreateSession(serverType)
                .thenCompose(session -> sessionManager.initializeSession(session)
                        .thenApply(initialized -> {
                            if (initialized) {
                                log.info("✅ MCP连接初始化成功: {}", serverType);
                                return Map.of(
                    "jsonrpc", "2.0",
                    "id", 1,
                                    "result", Map.of(
                        "protocolVersion", "2024-11-05",
                                        "capabilities", Map.of("tools", Map.of("listChanged", true)),
                                        "serverInfo", Map.of(
                            "name", "traveling-mcp-server",
                            "version", "1.0.0"
                        )
                    )
                );
                            } else {
                                throw new RuntimeException("MCP连接初始化失败: " + serverType);
                            }
                        }));
    }

    /**
     * 获取工具列表
     * 使用会话管理器统一处理
     */
    public CompletableFuture<List<Map<String, Object>>> getTools(String serverType) {
        return sessionManager.getTools(serverType);
    }

    /**
     * 调用工具
     * 使用会话管理器统一处理
     */
    public CompletableFuture<Map<String, Object>> callTool(String toolName, Map<String, Object> arguments, String serverType) {
        return sessionManager.callTool(toolName, arguments, serverType);
    }

    /**
     * 获取所有可用的MCP服务器类型
     */
    public List<String> getAvailableServerTypes() {
        return sessionManager.getAvailableServerTypes();
    }
}
