package com.zhouruojun.amadeusmcp.controller;

import com.zhouruojun.amadeusmcp.service.MCPService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * MCP协议控制器
 * 提供标准的MCP协议端点
 */
@RestController
@RequestMapping("/mcp")
@Slf4j
public class MCPController {

    @Autowired
    private MCPService mcpService;

    /**
     * MCP协议端点 - 处理所有MCP请求
     * 注意：必须使用同步返回以避免WebClient连接池问题
     */
    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> handleMCPRequest(
            @RequestBody Map<String, Object> request) {
        
        log.debug("收到MCP请求: {}", request);
        
        try {
            // 同步等待CompletableFuture完成
            Map<String, Object> result = mcpService.handleRequest(request).join();
            return ResponseEntity.ok(result);
        } catch (Exception throwable) {
            log.error("MCP请求处理失败", throwable);
            return ResponseEntity.internalServerError()
                    .body(Map.of(
                        "jsonrpc", "2.0",
                        "id", request.get("id"),
                        "error", Map.of(
                            "code", -32603,
                            "message", "Internal error: " + throwable.getMessage()
                        )
                    ));
        }
    }

    /**
     * MCP服务器信息端点
     */
    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> getMCPInfo() {
        return ResponseEntity.ok(Map.of(
            "name", "amadeus-mcp-server",
            "version", "1.0.0",
            "description", "Amadeus Travel API MCP Server - 航班、酒店和目的地体验服务",
            "protocol", "MCP 2024-11-05",
            "capabilities", Map.of(
                "tools", Map.of("listChanged", true)
            ),
            "endpoints", Map.of(
                "jsonrpc", "/mcp/jsonrpc",
                "info", "/mcp/info",
                "health", "/mcp/health"
            )
        ));
    }

    /**
     * 健康检查端点
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        return ResponseEntity.ok(Map.of(
            "status", "healthy",
            "timestamp", System.currentTimeMillis(),
            "server", "amadeus-mcp-server"
        ));
    }
}
