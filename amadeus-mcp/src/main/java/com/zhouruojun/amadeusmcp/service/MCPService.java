package com.zhouruojun.amadeusmcp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * MCP服务实现
 * 实现MCP协议，提供Amadeus Travel API工具
 */
@Slf4j
@Service
public class MCPService {

    @Autowired
    private AmadeusToolService amadeusToolService;
    
    private final ObjectMapper objectMapper = new ObjectMapper();

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
                    "name", "amadeus-mcp-server",
                    "version", "1.0.0"
                )
            )
        );
    }

    private Map<String, Object> handleToolsList(Object id, Map<String, Object> params) {
        try {
            List<Map<String, Object>> tools = amadeusToolService.getAvailableTools();
            
            return Map.of(
                "jsonrpc", "2.0",
                "id", id,
                "result", Map.of("tools", tools)
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
                log.warn("工具参数不是Map或String类型: {}, 使用空参数", argumentsObj != null ? argumentsObj.getClass().getSimpleName() : "null");
                arguments = new HashMap<>();
            }

            log.debug("调用工具: {}, 参数: {}", toolName, arguments);
            
            // 调用Amadeus工具服务
            Map<String, Object> result = amadeusToolService.callTool(toolName, arguments);

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
        Map<String, Object> response = new HashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", id != null ? id : "unknown");
        response.put("error", Map.of(
            "code", code,
            "message", message
        ));
        return response;
    }
}
