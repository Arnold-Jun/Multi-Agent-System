package com.zhouruojun.mcpserver.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhouruojun.mcpserver.config.MCPServerConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.File;
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
    private final MCPProcessManager processManager;
    
    // 会话状态管理
    private final Map<String, MCPSession> activeSessions = new ConcurrentHashMap<>();
    
    @Autowired
    public MCPSessionManager(MCPServerConfig mcpServerConfig, MCPProcessManager processManager) {
        this.mcpServerConfig = mcpServerConfig;
        this.processManager = processManager;
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
        log.debug("创建新的MCP会话: {}", serverType);
        
        return session;
    }

    /**
     * 初始化MCP会话
     * 根据服务器类型智能选择模式：
     * - 进程模式：通过stdin/stdout通信
     * - 混合模式：启动进程 + HTTP通信
     * - HTTP模式：直接HTTP通信（小红书使用批量模式，其他使用标准模式）
     */
    public CompletableFuture<Boolean> initializeSession(MCPSession session) {
        if (session.isInitialized()) {
            return CompletableFuture.completedFuture(true);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                String serverType = session.getServerType();
                MCPServerConfig.MCPServerInfo serverInfo = mcpServerConfig.getServerInfo(serverType);
                
                // 检查是否为进程模式
                if (serverInfo != null && serverInfo.isProcessMode()) {
                    return initializeProcessSession(session, serverInfo);
                }
                
                // HTTP模式
                if (serverInfo != null && serverInfo.isHttpMode()) {
                    // 检查是否需要启动进程（混合模式）
                    if (serverInfo.isAutoStart()) {
                        log.debug("混合模式启动进程: {}", serverType);
                        if (!startProcessForHybridMode(serverInfo, serverType)) {
                            log.error("混合模式进程启动失败: {}", serverType);
                            return false;
                        }
                    }
                    
                    // HTTP通信：根据服务器类型选择模式
                    if (isXiaohongshuServer(serverType)) {
                        log.debug("小红书MCP使用批量模式: {}", serverType);
                        session.setRequiresBatchMode(true);
                        
                        if (initializeBatchSession(session)) {
                            log.debug("小红书MCP初始化成功: {}", serverType);
                            session.setInitialized(true);
                            return true;
                        } else {
                            log.error("小红书MCP批量模式初始化失败: {}", serverType);
                            return false;
                        }
                    } else {
                        // 其他MCP服务器：使用标准模式
                        if (initializeStandardSession(session)) {
                            log.debug("MCP会话初始化成功: {}", serverType);
                            session.setInitialized(true);
                            return true;
                        } else {
                            log.error("标准模式初始化失败: {}", serverType);
                            return false;
                        }
                    }
                }
                
                // 如果没有匹配的配置，返回false
                log.error("未找到匹配的MCP服务器配置: {}", serverType);
                return false;
            } catch (Exception e) {
                log.error("MCP会话初始化异常: {}", session.getServerType(), e);
                return false;
            }
        });
    }
    
    /**
     * 混合模式：启动进程
     * 返回true表示进程启动成功或已运行
     */
    private boolean startProcessForHybridMode(MCPServerConfig.MCPServerInfo serverInfo, String serverType) {
        try {
            // 检查进程是否已运行
            if (processManager.isProcessRunning(serverType)) {
                log.debug("混合模式进程已运行: {}", serverType);
                return true;
            }
            
            cleanupPortBeforeStart(serverType);
            
            // 特殊处理xiaohongshu-mcp：启动Go进程
            if ("xiaohongshu-mcp".equals(serverType)) {
                log.debug("启动Go进程: {}", serverType);
                processManager.startMCPProcess(serverType, "go", 
                    Arrays.asList("run", "."), "C:\\Users\\ZhuanZ1\\mcp\\xiaohongshu-mcp").join();
            } 
            // 特殊处理amadeus-mcp：启动Java进程
            else if ("amadeus-mcp".equals(serverType)) {
                String amadeusWorkingDir = System.getProperty("user.dir") + File.separator + "amadeus-mcp";
                
                // 清理端口18090避免冲突
                processManager.forceCleanupPort(18090);
                Thread.sleep(2000);
                
                String jarPath = "target" + File.separator + "amadeus-mcp-1.0-SNAPSHOT.jar";
                File jarFile = new File(amadeusWorkingDir, jarPath);
                
                if (!jarFile.exists()) {
                    throw new RuntimeException("JAR文件不存在: " + jarFile.getAbsolutePath());
                }
                
                processManager.startMCPProcess(serverType, "java", 
                    Arrays.asList("-jar", jarPath), amadeusWorkingDir).join();
            } else {
                log.debug("启动进程: {} - {} {}", serverType, serverInfo.getCommand(),
                    serverInfo.getArgs() != null ? String.join(" ", serverInfo.getArgs()) : "");
                
                processManager.startMCPProcess(serverType, serverInfo.getCommand(), 
                    serverInfo.getArgs(), serverInfo.getWorkingDirectory(), serverInfo.getEnvironmentVariables()).join();
            }
            
            int delaySeconds = serverInfo.getStartupDelaySeconds();
            log.debug("等待进程就绪: {} 秒", delaySeconds);
            Thread.sleep(delaySeconds * 1000L);
            
            if (processManager.isProcessRunning(serverType)) {
                log.debug("进程启动成功: {}", serverType);
                return true;
            } else {
                log.error("混合模式进程启动后停止: {}", serverType);
                return false;
            }
        } catch (Exception e) {
            log.error("混合模式进程启动失败: {}", serverType, e);
            return false;
        }
    }
    
    /**
     * 启动前清理端口占用
     */
    private void cleanupPortBeforeStart(String serverType) {
        try {
            int[] commonPorts = {18060, 18061, 18062, 18063, 18064, 18065, 18066, 18067, 18068, 18069, 18070};
            
            for (int port : commonPorts) {
                if (!isPortAvailable(port)) {
                    log.warn("端口 {} 被占用，可能是 {} 的残留进程，尝试清理", port, serverType);
                    processManager.forceCleanupPort(port);
                    
                    Thread.sleep(2000);
                    if (isPortAvailable(port)) {
                        log.info("端口 {} 已释放", port);
                    } else {
                        log.warn("端口 {} 仍然被占用", port);
                    }
                }
            }
        } catch (Exception e) {
            log.error("启动前清理端口时发生异常: {}", serverType, e);
        }
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
            log.warn("标准模式初始化异常: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 批量模式初始化（适用于小红书等特殊MCP服务器）
     */
    private boolean initializeBatchSession(MCPSession session) {
        try {
            // 批量模式：初始化是隐式的，不需要显式调用
            log.debug("批量模式会话已准备就绪: {}", session.getServerType());
            return true;
        } catch (Exception e) {
            log.error("批量模式初始化异常: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 进程模式初始化
     */
    private boolean initializeProcessSession(MCPSession session, MCPServerConfig.MCPServerInfo serverInfo) {
        try {
            String serverType = session.getServerType();
            
            if (serverInfo == null || !serverInfo.isProcessMode()) {
                log.error("进程配置不存在或不是进程模式: {}", serverType);
                return false;
            }
            
            // 检查进程是否已运行
            if (processManager.isProcessRunning(serverType)) {
                log.info("MCP进程已运行: {}", serverType);
                session.setInitialized(true);
                return true;
            }
            
            // 启动进程
            log.info("启动MCP进程: {} - {} {}", serverType, serverInfo.getCommand(),
                serverInfo.getArgs() != null ? String.join(" ", serverInfo.getArgs()) : "");

            try {
                // 启动进程（内部已有3.5秒等待）
                processManager.startMCPProcess(serverType, serverInfo.getCommand(), 
                    serverInfo.getArgs(), serverInfo.getWorkingDirectory(), serverInfo.getEnvironmentVariables()).join();
                
                // MCPProcessManager已经等待了3.5秒，这里再等待1秒确保完全就绪
                log.info("等待进程稳定: {}", serverType);
                Thread.sleep(1000);
                
                if (processManager.isProcessRunning(serverType)) {
                    Map<String, Object> status = processManager.getProcessStatus(serverType);
                    log.info("MCP进程启动成功: {} - {}", serverType, status);
                    session.setInitialized(true);
                    return true;
                } else {
                    log.error("MCP进程启动失败: {} - 进程启动后立即停止", serverType);
                    Map<String, Object> status = processManager.getProcessStatus(serverType);
                    log.error("进程状态: {}", status);
                    return false;
                }
            } catch (Exception e) {
                log.error("MCP进程启动异常: {} - {}", serverType, e.getMessage(), e);
                return false;
            }
            
        } catch (Exception e) {
            log.error("进程模式初始化异常: {}", e.getMessage());
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
     * - 进程模式：通过stdin/stdout通信
     * - HTTP模式（小红书MCP）：使用批量模式
     * - HTTP模式（其他MCP）：使用标准模式
     */
    private CompletableFuture<Map<String, Object>> executeToolCall(MCPSession session, String toolName, Map<String, Object> arguments) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.debug("工具调用: {} ({})", toolName, session.getServerType());
                
                MCPServerConfig.MCPServerInfo serverInfo = mcpServerConfig.getServerInfo(session.getServerType());
                
                if (serverInfo != null && serverInfo.isProcessMode()) {
                    return callToolProcess(session, toolName, arguments);
                } else {
                    if (isXiaohongshuServer(session.getServerType())) {
                        return callToolBatch(session, toolName, arguments);
                    } else {
                        return callToolStandard(session, toolName, arguments);
                    }
                }
            } catch (Exception e) {
                log.error("工具调用失败: {} ({})", toolName, session.getServerType(), e);
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
            return result;
        } else {
            throw new RuntimeException("工具调用失败: " + result.get("error"));
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
                    return response;
                } else if (response.containsKey("error")) {
                    throw new RuntimeException("工具调用失败: " + response.get("error"));
                }
            }
        }
        
        throw new RuntimeException("批量响应中未找到工具调用结果");
    }

    /**
     * 进程模式工具调用
     */
    private Map<String, Object> callToolProcess(MCPSession session, String toolName, Map<String, Object> arguments) throws JsonProcessingException {
        Map<String, Object> request = Map.of(
            "jsonrpc", "2.0",
            "id", System.currentTimeMillis(),
            "method", "tools/call",
            "params", Map.of(
                "name", toolName,
                "arguments", arguments != null ? arguments : Map.of()
            )
        );

        
        try {
            Map<String, Object> response = processManager.sendRequest(session.getServerType(), request).join();
            
            if (response.containsKey("result")) {
                return response;
            } else {
                throw new RuntimeException("工具调用失败: " + response.get("error"));
            }
        } catch (Exception e) {
            log.error("进程模式工具调用异常: {}", e.getMessage());
            throw new RuntimeException("进程模式工具调用失败: " + e.getMessage(), e);
        }
    }



    /**
     * 获取工具列表
     * 支持HTTP模式和进程模式
     */
    public CompletableFuture<List<Map<String, Object>>> getTools(String serverType) {
        return getOrCreateSession(serverType)
                .thenCompose(session -> initializeSession(session)
                        .thenCompose(initialized -> {
                            if (!initialized) {
                                log.error("会话初始化失败，无法获取工具列表: {}", serverType);
                                return CompletableFuture.completedFuture(new ArrayList<>());
                            }
                            
                            MCPServerConfig.MCPServerInfo serverInfo = mcpServerConfig.getServerInfo(serverType);
                            
                            // 检查是否为进程模式
                            if (serverInfo != null && serverInfo.isProcessMode()) {
                                return getToolsProcess(session);
                            } else {
                                // HTTP模式：根据服务器类型选择模式
                                if (isXiaohongshuServer(serverType)) {
                                    log.debug("小红书MCP获取工具列表: {}", serverType);
                                    return getToolsBatch(session);
                                } else {
                                    log.debug("其他MCP服务器获取工具列表: {}", serverType);
                                    return getToolsStandard(session);
                                }
                            }
                        }));
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
                    
                    log.debug("获取到 {} 个工具 ({})", tools.size(), session.getServerType());
                    return tools;
                } else {
                    throw new RuntimeException("标准模式获取工具列表失败: " + result.get("error"));
                }
            } catch (Exception e) {
                log.error("标准模式获取工具列表失败 ({})", session.getServerType(), e);
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
                                
                                log.debug("批量模式获取到 {} 个工具 ({})", tools.size(), session.getServerType());
                                return tools;
                            }
                        }
                    }
                }
                
                log.warn("批量模式未找到工具列表响应 ({})", session.getServerType());
                return new ArrayList<>();
                
            } catch (Exception e) {
                log.error("批量模式获取工具列表失败 ({})", session.getServerType(), e);
                return new ArrayList<>();
            }
        });
    }

    /**
     * 进程模式获取工具列表
     */
    private CompletableFuture<List<Map<String, Object>>> getToolsProcess(MCPSession session) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Map<String, Object> request = Map.of(
                    "jsonrpc", "2.0",
                    "id", 2,
                    "method", "tools/list",
                    "params", Map.of()
                );

                log.debug("进程模式获取工具列表: {}", session.getServerType());
                
                Map<String, Object> response = processManager.sendRequest(session.getServerType(), request).join();
                
                if (response.containsKey("result")) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> resultData = (Map<String, Object>) response.get("result");
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> tools = (List<Map<String, Object>>) resultData.get("tools");
                    
                    log.debug("进程模式获取到 {} 个工具 ({})", tools.size(), session.getServerType());
                    return tools;
                } else {
                    throw new RuntimeException("进程模式获取工具列表失败: " + response.get("error"));
                }
            } catch (Exception e) {
                log.error("进程模式获取工具列表失败 ({})", session.getServerType(), e);
                throw new RuntimeException("进程模式获取工具列表失败: " + e.getMessage(), e);
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
     * 检查会话是否已初始化
     */
    public boolean isSessionInitialized(String serverType) {
        String sessionKey = serverType.toLowerCase();
        MCPSession session = activeSessions.get(sessionKey);
        
        if (session == null) {
            return false;
        }
        
        MCPServerConfig.MCPServerInfo serverInfo = mcpServerConfig.getServerInfo(serverType);
        
        // 如果是进程模式，检查进程是否还在运行
        if (serverInfo != null && serverInfo.isProcessMode()) {
            return session.isInitialized() && processManager.isProcessRunning(serverType);
        }
        
        return session.isInitialized();
    }

    /**
     * 根据服务器类型获取MCP连接信息
     * 支持HTTP和进程模式
     * 注意：对于进程模式，返回特殊的process://前缀仅用于会话标识，实际通信通过stdin/stdout
     */
    private String getMcpUrl(String serverType) {
        MCPServerConfig.MCPServerInfo serverInfo = mcpServerConfig.getServerInfo(serverType);
        
        if (serverInfo != null) {
            if (serverInfo.isProcessMode()) {
                // 进程模式返回特殊标识（仅用于会话标识，不用于实际通信）
                return "process://" + serverType;
            } else if (serverInfo.isHttpMode()) {
                return serverInfo.getUrl();
            }
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
     * 获取需要自动启动的服务器列表
     * 包括：进程模式 或 autoStart=true的服务器
     */
    public List<String> getNeedAutoStartServers() {
        Map<String, MCPServerConfig.MCPServerInfo> enabledServers = mcpServerConfig.getEnabledServers();
        List<String> needAutoStart = new ArrayList<>();
        
        for (Map.Entry<String, MCPServerConfig.MCPServerInfo> entry : enabledServers.entrySet()) {
            String serverType = entry.getKey();
            MCPServerConfig.MCPServerInfo serverInfo = entry.getValue();
            
            log.debug("检查服务器配置: {} - command: {}, url: {}", 
                serverType, serverInfo.getCommand(), serverInfo.getUrl());
            
            // 进程模式：有command字段
            if (serverInfo.isProcessMode()) {
                log.debug("发现进程模式服务器: {}", serverType);
                needAutoStart.add(serverType);
            }
            // 混合模式：autoStart=true
            else if (serverInfo.isAutoStart()) {
                log.debug("发现混合模式服务器: {}", serverType);
                needAutoStart.add(serverType);
            }
        }
        
        log.info("需要自动启动的服务器: {}", needAutoStart);
        return needAutoStart;
    }

    /**
     * 清理过期会话
     */
    public void cleanupExpiredSessions() {
        activeSessions.entrySet().removeIf(entry -> isSessionExpired(entry.getValue()));
        log.info("清理过期会话，当前活跃会话数: {}", activeSessions.size());
    }

    /**
     * 重新加载MCP配置
     * 清除所有会话和进程，重新从配置文件加载配置
     */
    public void reloadConfiguration() {
        log.info("重新加载MCP配置");
        
        // 停止所有进程
        processManager.stopAllProcesses();
        log.info("已停止所有MCP进程");
        
        // 清除所有现有会话
        activeSessions.clear();
        log.info("已清除所有会话");
        
        // 重新加载配置
        mcpServerConfig.reloadConfiguration();
        
        log.info("MCP配置重新加载完成，可用服务器: {}", getAvailableServerTypes());
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

    /**
     * 获取进程调试信息
     */
    public Map<String, Object> getProcessDebugInfo() {
        Map<String, Object> debugInfo = new HashMap<>();
        
        // 获取所有配置的服务器
        Map<String, MCPServerConfig.MCPServerInfo> enabledServers = mcpServerConfig.getEnabledServers();
        
        debugInfo.put("configuredServers", enabledServers.size());
        debugInfo.put("processServers", enabledServers.entrySet().stream()
            .filter(entry -> {
                MCPServerConfig.MCPServerInfo serverInfo = entry.getValue();
                return serverInfo.isProcessMode() || serverInfo.isAutoStart();
            })
            .collect(java.util.stream.Collectors.toMap(
                Map.Entry::getKey,
                entry -> {
                    String serverName = entry.getKey();
                    MCPServerConfig.MCPServerInfo serverInfo = entry.getValue();
                    Map<String, Object> serverDebug = new HashMap<>();
                    
                    if (serverInfo.isProcessMode()) {
                        serverDebug.put("command", serverInfo.getCommand());
                        serverDebug.put("args", serverInfo.getArgs());
                        serverDebug.put("workingDirectory", serverInfo.getWorkingDirectory());
                        serverDebug.put("autoStart", serverInfo.isAutoStart());
                        serverDebug.put("startupDelaySeconds", serverInfo.getStartupDelaySeconds());
                    }
                    
                    serverDebug.put("enabled", true);
                    serverDebug.put("url", serverInfo.getUrl());
                    serverDebug.put("isProcessMode", serverInfo.isProcessMode());
                    serverDebug.put("isHttpMode", serverInfo.isHttpMode());
                    serverDebug.put("processStatus", processManager.getProcessStatus(serverName));
                    serverDebug.put("isRunning", processManager.isProcessRunning(serverName));
                    return serverDebug;
                }
            ))
        );
        
        debugInfo.put("processStats", processManager.getProcessStats());
        
        return debugInfo;
    }

    /**
     * 检查端口冲突
     */
    public Map<String, Object> checkPortConflicts() {
        Map<String, Object> portInfo = new HashMap<>();
        
        // 检查常用端口
        int[] commonPorts = {18060, 18061, 18062, 18063, 18064, 18083};
        Map<String, Object> portStatus = new HashMap<>();
        
        for (int port : commonPorts) {
            boolean isAvailable = isPortAvailable(port);
            portStatus.put(String.valueOf(port), Map.of(
                "available", isAvailable,
                "status", isAvailable ? "可用" : "被占用"
            ));
        }
        
        portInfo.put("commonPorts", portStatus);
        portInfo.put("timestamp", System.currentTimeMillis());
        
        return portInfo;
    }

    /**
     * 检查端口是否可用
     */
    private boolean isPortAvailable(int port) {
        try (java.net.ServerSocket serverSocket = new java.net.ServerSocket(port)) {
            return true;
        } catch (java.io.IOException e) {
            return false;
        }
    }
}

