package com.zhouruojun.mcpserver.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhouruojun.mcpserver.config.MCPServerConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP会话管理器
 * 统一管理不同MCP服务器的会话状态和请求模式
 * 支持通过mcp.json动态配置MCP服务器
 */
@Slf4j
@Component
public class MCPSessionManager {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final MCPServerConfig mcpServerConfig;
    
    // 会话状态管理
    private final Map<String, MCPSession> activeSessions = new ConcurrentHashMap<>();
    
    @Autowired
    public MCPSessionManager(MCPServerConfig mcpServerConfig) {
        this.mcpServerConfig = mcpServerConfig;
        this.webClient = WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(1024 * 1024))
                .defaultHeader("Accept", "application/json, text/event-stream")
                .defaultHeader("Content-Type", "application/json")
                .defaultHeader("User-Agent", "traveling-mcp-server/1.0.0")
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * MCP会话信息
     */
    public static class MCPSession {
        private final String serverType;
        private final String mcpUrl;
        private final long createdAt;
        private volatile boolean initialized;
        private volatile long lastUsed;
        private volatile boolean requiresBatchMode; // 动态标记是否需要批量模式

        public MCPSession(String serverType, String mcpUrl) {
            this.serverType = serverType;
            this.mcpUrl = mcpUrl;
            this.createdAt = System.currentTimeMillis();
            this.lastUsed = System.currentTimeMillis();
            this.requiresBatchMode = false; // 默认使用标准模式
        }

        // Getters and setters
        public String getServerType() { return serverType; }
        public String getMcpUrl() { return mcpUrl; }
        public boolean isRequiresBatchMode() { return requiresBatchMode; }
        public void setRequiresBatchMode(boolean requiresBatchMode) { this.requiresBatchMode = requiresBatchMode; }
        public boolean isInitialized() { return initialized; }
        public void setInitialized(boolean initialized) { this.initialized = initialized; }
        public long getLastUsed() { return lastUsed; }
        public void updateLastUsed() { this.lastUsed = System.currentTimeMillis(); }
        public long getCreatedAt() { return createdAt; }
    }

    /**
     * 获取或创建MCP会话
     */
    public CompletableFuture<MCPSession> getOrCreateSession(String serverType) {
        String sessionKey = serverType.toLowerCase();
        
        return CompletableFuture.supplyAsync(() -> {
            MCPSession session = activeSessions.get(sessionKey);
            
            if (session == null || isSessionExpired(session)) {
                session = createNewSession(serverType);
                activeSessions.put(sessionKey, session);
            }
            
            session.updateLastUsed();
            return session;
        });
    }

    /**
     * 创建新的MCP会话
     */
    private MCPSession createNewSession(String serverType) {
        String mcpUrl = getMcpUrl(serverType);
        
        MCPSession session = new MCPSession(serverType, mcpUrl);
        log.info("🆕 创建新的MCP会话: {} (默认标准模式)", serverType);
        
        return session;
    }

    /**
     * 初始化MCP会话
     * 根据服务器类型智能选择模式：
     * - 小红书MCP：直接使用批量模式
     * - 其他MCP：先尝试标准模式，失败后降级到批量模式
     */
    public CompletableFuture<Boolean> initializeSession(MCPSession session) {
        if (session.isInitialized()) {
            return CompletableFuture.completedFuture(true);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                // 检查是否为小红书MCP，直接使用批量模式
                if (isXiaohongshuServer(session.getServerType())) {
                    log.info("🔄 小红书MCP，直接使用批量模式: {}", session.getServerType());
                    session.setRequiresBatchMode(true);
                    
                    if (initializeBatchSession(session)) {
                        log.info("✅ 小红书MCP批量模式初始化成功: {}", session.getServerType());
                        session.setInitialized(true);
                        return true;
                    } else {
                        log.error("❌ 小红书MCP批量模式初始化失败: {}", session.getServerType());
                        return false;
                    }
                } else {
                    // 其他MCP服务器：只使用标准模式，不降级到批量模式
                    if (initializeStandardSession(session)) {
                        session.setInitialized(true);
                        return true;
                    } else {
                        log.error("❌ 标准模式初始化失败: {} (其他MCP服务器不支持批量模式)", session.getServerType());
                        return false;
                    }
                }
            } catch (Exception e) {
                log.error("❌ MCP会话初始化异常: {}", session.getServerType(), e);
                return false;
            }
        });
    }

    /**
     * 标准模式初始化（适用于大多数MCP服务器）
     */
    private boolean initializeStandardSession(MCPSession session) {
        try {
            Map<String, Object> request = Map.of(
                "jsonrpc", "2.0",
                "id", 1,
                "method", "initialize",
                "params", Map.of(
                    "protocolVersion", "2024-11-05",
                    "capabilities", Map.of(),
                    "clientInfo", Map.of(
                        "name", "traveling-mcp-server",
                        "version", "1.0.0"
                    )
                )
            );

            String response = webClient.post()
                    .uri(session.getMcpUrl())
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();

            @SuppressWarnings("unchecked")
            Map<String, Object> result = objectMapper.readValue(response, Map.class);
            
            if (result.containsKey("result")) {
                return true;
            } else {
                return false;
            }
        } catch (Exception e) {
            log.warn("⚠️ 标准模式初始化异常: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 批量模式初始化（适用于小红书等特殊MCP服务器）
     */
    private boolean initializeBatchSession(MCPSession session) {
        try {
            // 批量模式：初始化是隐式的，不需要显式调用
            log.info("🔄 批量模式会话已准备就绪: {}", session.getServerType());
            return true;
        } catch (Exception e) {
            log.error("❌ 批量模式初始化异常: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 判断是否为小红书MCP服务器
     */
    private boolean isXiaohongshuServer(String serverType) {
        String lowerServerType = serverType.toLowerCase();
        return lowerServerType.contains("xiaohongshu") || 
               lowerServerType.contains("xhs") ||
               lowerServerType.contains("小红书");
    }


    /**
     * 调用MCP工具（统一接口）
     */
    public CompletableFuture<Map<String, Object>> callTool(String toolName, Map<String, Object> arguments, String serverType) {
        return getOrCreateSession(serverType)
                .thenCompose(session -> initializeSession(session)
                        .thenCompose(initialized -> {
                            if (!initialized) {
                                return CompletableFuture.failedFuture(
                                    new RuntimeException("MCP会话初始化失败: " + serverType));
                            }
                            
                            return executeToolCall(session, toolName, arguments);
                        }));
    }

    /**
     * 执行工具调用
     * 根据服务器类型智能选择模式：
     * - 小红书MCP：直接使用批量模式
     * - 其他MCP：先尝试标准模式，失败后降级到批量模式
     */
    private CompletableFuture<Map<String, Object>> executeToolCall(MCPSession session, String toolName, Map<String, Object> arguments) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 检查是否为小红书MCP，直接使用批量模式
                if (isXiaohongshuServer(session.getServerType())) {
                    log.info("🔄 小红书MCP工具调用，使用批量模式: {}", toolName);
                    return callToolBatch(session, toolName, arguments);
                } else {
                    // 其他MCP服务器：只使用标准模式，不降级到批量模式
                    return callToolStandard(session, toolName, arguments);
                }
            } catch (Exception e) {
                log.error("❌ 工具调用失败: {} ({})", toolName, session.getServerType(), e);
                throw new RuntimeException("工具调用失败: " + e.getMessage(), e);
            }
        });
    }

    /**
     * 标准模式工具调用（适用于大多数MCP服务器）
     */
    private Map<String, Object> callToolStandard(MCPSession session, String toolName, Map<String, Object> arguments) throws JsonProcessingException {
        Map<String, Object> request = Map.of(
            "jsonrpc", "2.0",
            "id", System.currentTimeMillis(),
            "method", "tools/call",
            "params", Map.of(
                "name", toolName,
                "arguments", arguments != null ? arguments : Map.of()
            )
        );
        
        String response = webClient.post()
                .uri(session.getMcpUrl())
                .bodyValue(request)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(30))
                .block();

        @SuppressWarnings("unchecked")
        Map<String, Object> result = objectMapper.readValue(response, Map.class);
        
        if (result.containsKey("result")) {
            log.info("✅ 标准模式工具调用成功: {}", toolName);
            return result;
        } else {
            throw new RuntimeException("标准模式工具调用失败: " + result.get("error"));
        }
    }

    /**
     * 批量模式工具调用（适用于小红书等特殊MCP服务器）
     */
    private Map<String, Object> callToolBatch(MCPSession session, String toolName, Map<String, Object> arguments) throws JsonProcessingException {
        List<Map<String, Object>> batchRequest = new ArrayList<>();
        
        // 请求1：初始化
        batchRequest.add(Map.of(
            "jsonrpc", "2.0",
            "id", 1,
            "method", "initialize",
            "params", Map.of(
                "protocolVersion", "2024-11-05",
                "capabilities", Map.of("tools", Map.of()),
                "clientInfo", Map.of(
                    "name", "traveling-mcp-server",
                    "version", "1.0.0"
                )
            )
        ));
        
        // 请求2：工具调用
        long callId = System.currentTimeMillis();
        batchRequest.add(Map.of(
            "jsonrpc", "2.0",
            "id", callId,
            "method", "tools/call",
            "params", Map.of(
                "name", toolName,
                "arguments", arguments != null ? arguments : Map.of()
            )
        ));
        
        log.info("🔧 批量模式调用工具: {} ({})", toolName, session.getServerType());
        
        String batchResponse = webClient.post()
                .uri(session.getMcpUrl())
                .bodyValue(batchRequest)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(30))
                .block();
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> responses = objectMapper.readValue(batchResponse, List.class);
        
        // 查找工具调用的响应
        for (Map<String, Object> response : responses) {
            Object id = response.get("id");
            if (id != null && id.equals(callId)) {
                if (response.containsKey("result")) {
                    log.info("✅ 批量模式工具调用成功: {}", toolName);
                    return response;
                } else if (response.containsKey("error")) {
                    throw new RuntimeException("批量模式工具调用失败: " + response.get("error"));
                }
            }
        }
        
        throw new RuntimeException("批量响应中未找到工具调用结果");
    }



    /**
     * 获取工具列表
     * 根据服务器类型智能选择模式：
     * - 小红书MCP：直接使用批量模式
     * - 其他MCP：先尝试标准模式，失败后降级到批量模式
     */
    public CompletableFuture<List<Map<String, Object>>> getTools(String serverType) {
        return getOrCreateSession(serverType)
                .thenCompose(session -> {
                    // 检查是否为小红书MCP，直接使用批量模式
                    if (isXiaohongshuServer(serverType)) {
                        log.info("🔄 小红书MCP获取工具列表，使用批量模式: {}", serverType);
                        return getToolsBatch(session);
                    } else {
                        // 其他MCP服务器：只使用标准模式，不降级到批量模式
                        log.info("🔧 其他MCP服务器获取工具列表，使用标准模式: {}", serverType);
                        return getToolsStandard(session);
                    }
                });
    }

    /**
     * 标准模式获取工具列表（适用于大多数MCP服务器）
     */
    private CompletableFuture<List<Map<String, Object>>> getToolsStandard(MCPSession session) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Map<String, Object> request = Map.of(
                    "jsonrpc", "2.0",
                    "id", 2,
                    "method", "tools/list",
                    "params", Map.of()
                );

                String response = webClient.post()
                        .uri(session.getMcpUrl())
                        .bodyValue(request)
                        .retrieve()
                        .bodyToMono(String.class)
                        .timeout(Duration.ofSeconds(30))
                        .block();

                @SuppressWarnings("unchecked")
                Map<String, Object> result = objectMapper.readValue(response, Map.class);
                
                if (result.containsKey("result")) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> resultData = (Map<String, Object>) result.get("result");
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> tools = (List<Map<String, Object>>) resultData.get("tools");
                    
                    log.info("✅ 标准模式获取到 {} 个工具 ({})", tools.size(), session.getServerType());
                    return tools;
                } else {
                    throw new RuntimeException("标准模式获取工具列表失败: " + result.get("error"));
                }
            } catch (Exception e) {
                log.error("❌ 标准模式获取工具列表失败 ({})", session.getServerType(), e);
                throw new RuntimeException("标准模式获取工具列表失败: " + e.getMessage(), e);
            }
        });
    }

    /**
     * 批量模式获取工具列表（适用于小红书等特殊MCP服务器）
     */
    private CompletableFuture<List<Map<String, Object>>> getToolsBatch(MCPSession session) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                List<Map<String, Object>> batchRequest = new ArrayList<>();
                
                // 请求1：初始化
                batchRequest.add(Map.of(
                    "jsonrpc", "2.0",
                    "id", 1,
                    "method", "initialize",
                    "params", Map.of(
                        "protocolVersion", "2024-11-05",
                        "capabilities", Map.of("tools", Map.of()),
                        "clientInfo", Map.of(
                            "name", "traveling-mcp-server",
                            "version", "1.0.0"
                        )
                    )
                ));
                
                // 请求2：工具列表
                batchRequest.add(Map.of(
                    "jsonrpc", "2.0",
                    "id", 2,
                    "method", "tools/list",
                    "params", Map.of()
                ));
                
                String batchResponse = webClient.post()
                        .uri(session.getMcpUrl())
                        .bodyValue(batchRequest)
                        .retrieve()
                        .bodyToMono(String.class)
                        .timeout(Duration.ofSeconds(30))
                        .block();
                
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> responses = objectMapper.readValue(batchResponse, List.class);
                
                // 查找tools/list的响应
                for (Map<String, Object> response : responses) {
                    Object id = response.get("id");
                    if (id != null && id.equals(2)) {
                        if (response.containsKey("result")) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> resultData = (Map<String, Object>) response.get("result");
                            
                            if (resultData.containsKey("tools")) {
                                @SuppressWarnings("unchecked")
                                List<Map<String, Object>> tools = (List<Map<String, Object>>) resultData.get("tools");
                                
                                log.info("✅ 批量模式获取到 {} 个工具 ({})", tools.size(), session.getServerType());
                                return tools;
                            }
                        }
                    }
                }
                
                log.warn("⚠️ 批量模式未找到工具列表响应 ({})", session.getServerType());
                return new ArrayList<>();
                
            } catch (Exception e) {
                log.error("❌ 批量模式获取工具列表失败 ({})", session.getServerType(), e);
                return new ArrayList<>();
            }
        });
    }



    /**
     * 检查会话是否过期
     */
    private boolean isSessionExpired(MCPSession session) {
        long now = System.currentTimeMillis();
        long sessionTimeout = 30 * 60 * 1000; // 30分钟超时
        
        return (now - session.getLastUsed()) > sessionTimeout;
    }

    /**
     * 根据服务器类型获取MCP URL
     * 支持从mcp.json动态加载配置
     */
    private String getMcpUrl(String serverType) {
        MCPServerConfig.MCPServerInfo serverInfo = mcpServerConfig.getServerInfo(serverType);
        
        if (serverInfo != null && serverInfo.isEnabled()) {
            return serverInfo.getUrl();
        }
        
        throw new IllegalArgumentException("MCP服务器未配置或未启用: " + serverType);
    }

    /**
     * 获取所有可用的MCP服务器类型
     * 从mcp.json动态加载
     */
    public List<String> getAvailableServerTypes() {
        Map<String, MCPServerConfig.MCPServerInfo> enabledServers = mcpServerConfig.getEnabledServers();
        return new ArrayList<>(enabledServers.keySet());
    }

    /**
     * 清理过期会话
     */
    public void cleanupExpiredSessions() {
        activeSessions.entrySet().removeIf(entry -> isSessionExpired(entry.getValue()));
        log.info("🧹 清理过期会话，当前活跃会话数: {}", activeSessions.size());
    }

    /**
     * 重新加载MCP配置
     * 清除所有会话，重新从mcp.json加载配置
     */
    public void reloadConfiguration() {
        log.info("🔄 重新加载MCP配置...");
        
        // 清除所有现有会话
        activeSessions.clear();
        log.info("🧹 已清除所有会话");
        
        // 重新加载配置
        mcpServerConfig.reloadConfiguration();
        
        log.info("✅ MCP配置重新加载完成，可用服务器: {}", getAvailableServerTypes());
    }

    /**
     * 获取会话统计信息
     */
    public Map<String, Object> getSessionStats() {
        return Map.of(
            "activeSessions", activeSessions.size(),
            "sessions", activeSessions.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                    Map.Entry::getKey,
                    entry -> Map.of(
                        "serverType", entry.getValue().getServerType(),
                        "initialized", entry.getValue().isInitialized(),
                        "lastUsed", entry.getValue().getLastUsed(),
                        "createdAt", entry.getValue().getCreatedAt()
                    )
                ))
        );
    }
}

