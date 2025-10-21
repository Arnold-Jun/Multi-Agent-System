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
    private MCPClient mcpClient;
    
    @Autowired
    private MCPSessionManager sessionManager;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // 工具注册表：工具名称 -> 服务器类型
    private final Map<String, String> toolToServerMap = new java.util.concurrent.ConcurrentHashMap<>();
    
    // 是否已初始化工具注册表
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
                log.error("❌ MCP请求处理失败", e);
                return createErrorResponse(request.get("id"), -32603, "Internal error: " + e.getMessage());
            }
        });
    }

    /**
     * 处理初始化请求
     */
    private Map<String, Object> handleInitialize(Object id, Map<String, Object> params) {
        log.info("🚀 MCP服务器初始化");
        
        Map<String, Object> capabilities = new HashMap<>();
        capabilities.put("tools", Map.of("listChanged", true));
        
        Map<String, Object> serverInfo = new HashMap<>();
        serverInfo.put("name", "traveling-mcp-server");
        serverInfo.put("version", "1.0.0");

        return Map.of(
            "jsonrpc", "2.0",
            "id", id,
            "result", Map.of(
                "protocolVersion", "2024-11-05",
                "capabilities", capabilities,
                "serverInfo", serverInfo
            )
        );
    }

    /**
     * 初始化工具注册表
     * 从所有MCP服务器获取工具列表并建立映射关系
     */
    private void initializeToolRegistry() {
        if (toolRegistryInitialized) {
            return;
        }
        
        synchronized (this) {
            if (toolRegistryInitialized) {
                return;
            }
            
            log.info("🔄 初始化工具注册表...");
            toolToServerMap.clear();
            
            try {
                List<String> serverTypes = mcpClient.getAvailableServerTypes();
                
                for (String serverType : serverTypes) {
                    try {
                        List<Map<String, Object>> tools = mcpClient.getTools(serverType).join();
                        
                        for (Map<String, Object> tool : tools) {
                            String toolName = (String) tool.get("name");
                            if (toolName != null) {
                                toolToServerMap.put(toolName, serverType);
                                log.debug("  📌 注册工具: {} -> {}", toolName, serverType);
                            }
                        }
                        
                        log.info("✅ 从 {} 注册了 {} 个工具", serverType, tools.size());
                    } catch (Exception e) {
                        log.warn("⚠️ 从 {} 获取工具列表失败: {}", serverType, e.getMessage());
                    }
                }
                
                log.info("✅ 工具注册表初始化完成，共注册 {} 个工具", toolToServerMap.size());
                toolRegistryInitialized = true;
                
            } catch (Exception e) {
                log.error("❌ 工具注册表初始化失败", e);
            }
        }
    }
    
    /**
     * 处理工具列表请求
     */
    private Map<String, Object> handleToolsList(Object id, Map<String, Object> params) {
        log.info("📋 获取工具列表");
        
        // 确保工具注册表已初始化
        if (!toolRegistryInitialized) {
            initializeToolRegistry();
        }
        
        try {
            // 获取所有可用的MCP服务器类型
            List<String> serverTypes = mcpClient.getAvailableServerTypes();
            List<Map<String, Object>> allTools = new ArrayList<>();
            
            // 从所有MCP服务器获取工具列表
            for (String serverType : serverTypes) {
                try {
                    List<Map<String, Object>> tools = mcpClient.getTools(serverType).join();
                    // 为每个工具添加服务器类型标识
                    for (Map<String, Object> tool : tools) {
                        tool.put("serverType", serverType);
                    }
                    allTools.addAll(tools);
                    log.info("✅ 从 {} 获取到 {} 个工具", serverType, tools.size());
                } catch (Exception e) {
                    log.warn("⚠️ 从 {} 获取工具列表失败: {}", serverType, e.getMessage());
                }
            }
            
            return Map.of(
                "jsonrpc", "2.0",
                "id", id,
                "result", Map.of("tools", allTools)
            );

        } catch (Exception e) {
            log.error("❌ 获取工具列表失败", e);
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
                    log.info("✅ 成功解析JSON参数: {}", arguments);
                } catch (Exception e) {
                    log.warn("⚠️ 无法解析JSON参数: {}, 使用空参数", argumentsObj, e);
                    arguments = new HashMap<>();
                }
            } else {
                // 如果arguments不是Map或String类型，创建一个空的Map
                log.warn("⚠️ 工具参数不是Map或String类型: {}, 使用空参数", argumentsObj != null ? argumentsObj.getClass().getSimpleName() : "null");
                arguments = new HashMap<>();
            }

            log.info("🔧 调用工具: {}, 参数: {}", toolName, arguments);
            
            
            
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
            log.error("❌ 工具调用失败", e);
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

            // 调用相应的MCP服务器
            return mcpClient.callTool(toolName, arguments, serverType).join();
        } catch (Exception e) {
            log.error("❌ 工具调用失败: {}", toolName, e);
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
            log.info("🎯 工具 {} 匹配到服务器: {} (从注册表)", toolName, serverType);
            return serverType;
        }
        
        // 如果注册表中没有，可能是新工具，尝试重新加载注册表
        log.warn("⚠️ 工具 {} 在注册表中未找到，尝试重新加载注册表", toolName);
        toolRegistryInitialized = false;
        initializeToolRegistry();
        
        serverType = toolToServerMap.get(toolName);
        if (serverType != null) {
            log.info("🎯 工具 {} 匹配到服务器: {} (重新加载后)", toolName, serverType);
            return serverType;
        }
        
        // 如果还是找不到，抛出异常
        log.error("❌ 工具 {} 在任何MCP服务器中都未找到", toolName);
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
        return mcpClient.getAvailableServerTypes();
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
        // 重置工具注册表
        toolRegistryInitialized = false;
        toolToServerMap.clear();
        log.info("🔄 工具注册表已重置");
        
        // 重新加载会话管理器配置
        sessionManager.reloadConfiguration();
        
        // 重新初始化工具注册表
        initializeToolRegistry();
    }
}
