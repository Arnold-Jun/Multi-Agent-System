package com.zhouruojun.mcpserver.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * MCP服务器实现
 * 实现MCP协议，提供工具列表和工具调用功能
 */
@Slf4j
@Component
public class MCPServer {

    
    @Autowired
    private MCPSessionManager sessionManager;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    private final Map<String, String> toolToServerMap = new java.util.concurrent.ConcurrentHashMap<>();
    private volatile boolean toolRegistryInitialized = false;


    /**
     * 处理MCP请求
     */
    public CompletableFuture<Map<String, Object>> handleRequest(Map<String, Object> request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String method = (String) request.get("method");
                Object id = request.get("id");
                @SuppressWarnings("unchecked")
                Map<String, Object> params = (Map<String, Object>) request.getOrDefault("params", new HashMap<>());


                return switch (method) {
                    case "initialize" -> handleInitialize(id, params);
                    case "tools/list" -> handleToolsList(id, params);
                    case "tools/call" -> handleToolsCall(id, params);
                    case "ping" -> handlePing(id);
                    default -> createErrorResponse(id, -32601, "Method not found: " + method);
                };

            } catch (Exception e) {
                log.error("MCP请求处理失败", e);
                return createErrorResponse(request.get("id"), -32603, "Internal error: " + e.getMessage());
            }
        });
    }

    private Map<String, Object> handleInitialize(Object id, Map<String, Object> params) {
        return Map.of(
            "jsonrpc", "2.0",
            "id", id,
            "result", Map.of(
                "protocolVersion", "2024-11-05",
                "capabilities", Map.of("tools", Map.of("listChanged", true)),
                "serverInfo", Map.of(
                    "name", "traveling-mcp-server",
                    "version", "1.0.0"
                )
            )
        );
    }

    private void initializeToolRegistry() {
        if (toolRegistryInitialized) {
            return;
        }
        
        synchronized (this) {
            if (toolRegistryInitialized) {
                return;
            }
            
            toolToServerMap.clear();
            
            try {
                List<String> serverTypes = sessionManager.getAvailableServerTypes();
                
                for (String serverType : serverTypes) {
                    try {
                        List<Map<String, Object>> tools = sessionManager.getTools(serverType).join();
                        
                        for (Map<String, Object> tool : tools) {
                            String toolName = (String) tool.get("name");
                            if (toolName != null) {
                                toolToServerMap.put(toolName, serverType);
                            }
                        }
                        
                        log.info("注册工具: {} ({}个)", serverType, tools.size());
                    } catch (Exception e) {
                        log.warn("获取工具失败: {} - {}", serverType, e.getMessage());
                    }
                }
                
                log.info("工具注册完成: {}个", toolToServerMap.size());
                toolRegistryInitialized = true;
                
            } catch (Exception e) {
                log.error("工具注册失败", e);
            }
        }
    }
    
    private Map<String, Object> handleToolsList(Object id, Map<String, Object> params) {
        if (!toolRegistryInitialized) {
            initializeToolRegistry();
        }
        
        try {
            List<String> serverTypes = sessionManager.getAvailableServerTypes();
            List<Map<String, Object>> allTools = new ArrayList<>();
            
            for (String serverType : serverTypes) {
                try {
                    List<Map<String, Object>> tools = sessionManager.getTools(serverType).join();
                    for (Map<String, Object> tool : tools) {
                        tool.put("serverType", serverType);
                    }
                    allTools.addAll(tools);
                } catch (Exception e) {
                    log.warn("获取工具失败: {} - {}", serverType, e.getMessage());
                }
            }
            
            return Map.of(
                "jsonrpc", "2.0",
                "id", id,
                "result", Map.of("tools", allTools)
            );

        } catch (Exception e) {
            log.error("获取工具列表失败", e);
            return createErrorResponse(id, -32603, "Failed to get tools list: " + e.getMessage());
        }
    }

    /**
     * 处理工具调用请求
     */
    private Map<String, Object> handleToolsCall(Object id, Map<String, Object> params) {
        try {
            String toolName = (String) params.get("name");
            Object argumentsObj = params.getOrDefault("arguments", new HashMap<>());
            
            Map<String, Object> arguments;
            if (argumentsObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> argsMap = (Map<String, Object>) argumentsObj;
                arguments = argsMap;
            } else if (argumentsObj instanceof String) {
                // 如果arguments是JSON字符串，尝试解析
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> parsedArgs = objectMapper.readValue((String) argumentsObj, Map.class);
                    arguments = parsedArgs;
                    log.debug("成功解析JSON参数: {}", arguments);
                } catch (Exception e) {
                    log.warn("无法解析JSON参数: {}, 使用空参数", argumentsObj, e);
                    arguments = new HashMap<>();
                }
            } else {
                // 如果arguments不是Map或String类型，创建一个空的Map
                log.warn("工具参数不是Map或String类型: {}, 使用空参数", argumentsObj != null ? argumentsObj.getClass().getSimpleName() : "null");
                arguments = new HashMap<>();
            }

            log.debug("调用工具: {}, 参数: {}", toolName, arguments);
            
            // 根据工具名称调用相应的适配器
            Map<String, Object> result = callToolByName(toolName, arguments);

            return Map.of(
                "jsonrpc", "2.0",
                "id", id,
                "result", Map.of(
                    "content", Arrays.asList(Map.of(
                        "type", "text",
                        "text", result.toString()
                    ))
                )
            );

        } catch (Exception e) {
            log.error("工具调用失败", e);
            return createErrorResponse(id, -32603, "Tool execution failed: " + e.getMessage());
        }
    }

    /**
     * 根据工具名称调用相应的工具
     */
    private Map<String, Object> callToolByName(String toolName, Map<String, Object> arguments) {
        try {
            // 根据工具名称确定服务器类型
            String serverType = determineServerType(toolName);

            // 调用相应的MCP服务器 - 使用get()而不是join()避免死锁
            return sessionManager.callTool(toolName, arguments, serverType).get();
        } catch (Exception e) {
            log.error("工具调用失败: {}", toolName, e);
            throw new RuntimeException("工具调用失败: " + e.getMessage(), e);
        }
    }



    /**
     * 根据工具名称确定服务器类型
     * 从工具注册表中查找映射关系
     */
    private String determineServerType(String toolName) {
        // 确保工具注册表已初始化
        if (!toolRegistryInitialized) {
            initializeToolRegistry();
        }
        
        // 从工具注册表中查找
        String serverType = toolToServerMap.get(toolName);
        
        if (serverType != null) {
            log.debug("工具 {} 匹配到服务器: {} (从注册表)", toolName, serverType);
            return serverType;
        }
        
        // 如果注册表中没有，可能是新工具，尝试重新加载注册表
        log.warn("工具 {} 在注册表中未找到，尝试重新加载注册表", toolName);
        toolRegistryInitialized = false;
        initializeToolRegistry();
        
        serverType = toolToServerMap.get(toolName);
        if (serverType != null) {
            log.debug("工具 {} 匹配到服务器: {} (重新加载后)", toolName, serverType);
            return serverType;
        }
        
        // 如果还是找不到，抛出异常
        log.error("工具 {} 在任何MCP服务器中都未找到", toolName);
        throw new RuntimeException("工具 " + toolName + " 在任何MCP服务器中都未找到");
    }
    

    /**
     * 处理ping请求
     */
    private Map<String, Object> handlePing(Object id) {
        return Map.of(
            "jsonrpc", "2.0",
            "id", id,
            "result", Map.of()
        );
    }


    /**
     * 创建错误响应
     */
    private Map<String, Object> createErrorResponse(Object id, int code, String message) {
        return Map.of(
            "jsonrpc", "2.0",
            "id", id,
            "error", Map.of(
                "code", code,
                "message", message
            )
        );
    }

    /**
     * 获取可用的MCP服务器类型
     */
    public List<String> getAvailableServerTypes() {
        return sessionManager.getAvailableServerTypes();
    }

    /**
     * 获取会话统计信息
     */
    public Map<String, Object> getSessionStats() {
        return sessionManager.getSessionStats();
    }

    /**
     * 重新加载MCP配置
     */
    public void reloadConfiguration() {
        toolRegistryInitialized = false;
        toolToServerMap.clear();
        sessionManager.reloadConfiguration();
        initializeToolRegistry();
    }

    /**
     * 获取进程调试信息
     */
    public Map<String, Object> getProcessDebugInfo() {
        return sessionManager.getProcessDebugInfo();
    }

    /**
     * 检查端口冲突
     */
    public Map<String, Object> checkPortConflicts() {
        return sessionManager.checkPortConflicts();
    }

    /**
     * 强制清理所有进程和端口
     */
    public void forceCleanup() {
        reloadConfiguration();
    }
}
