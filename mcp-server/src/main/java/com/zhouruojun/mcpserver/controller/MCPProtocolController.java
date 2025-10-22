package com.zhouruojun.mcpserver.controller;

import com.zhouruojun.mcpserver.mcp.MCPServer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * MCP协议控制器
 * 提供标准的MCP协议端点
 */
@RestController
@RequestMapping("/mcp")
@Slf4j
public class MCPProtocolController {

    @Autowired
    private MCPServer mcpServer;
    
    @Autowired
    private com.zhouruojun.mcpserver.config.MCPServerConfig mcpServerConfig;

    /**
     * MCP协议端点
     * 处理MCP协议的JSON-RPC请求
     */
    @PostMapping(value = "/jsonrpc", produces = MediaType.APPLICATION_JSON_VALUE)
    public CompletableFuture<ResponseEntity<Map<String, Object>>> handleMCPRequest(
            @RequestBody Map<String, Object> request) {
        
        return mcpServer.handleRequest(request)
                .thenApply(ResponseEntity::ok)
                .exceptionally(throwable -> {
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
                });
    }

    /**
     * MCP服务器信息端点
     */
    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> getMCPInfo() {
        return ResponseEntity.ok(Map.of(
            "name", "traveling-mcp-server",
            "version", "1.0.0",
            "protocol", "MCP 2024-11-05",
            "capabilities", Map.of(
                "tools", Map.of("listChanged", true)
            ),
            "supportedServers", mcpServerConfig.getEnabledServers().entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                    Map.Entry::getKey,
                    entry -> entry.getValue().getDescription() != null ? 
                        entry.getValue().getDescription() : entry.getKey()
                )),
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
            "server", "traveling-mcp-server"
        ));
    }

    /**
     * 获取可用的MCP服务器列表
     */
    @GetMapping("/servers")
    public ResponseEntity<Map<String, Object>> getAvailableServers() {
        try {
            List<String> serverTypes = mcpServer.getAvailableServerTypes();
            return ResponseEntity.ok(Map.of(
                "availableServers", serverTypes,
                "count", serverTypes.size(),
                "timestamp", System.currentTimeMillis()
            ));
        } catch (Exception e) {
            log.error("获取可用服务器列表失败", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to get available servers: " + e.getMessage()));
        }
    }

    /**
     * 获取MCP会话统计信息
     */
    @GetMapping("/sessions")
    public ResponseEntity<Map<String, Object>> getSessionStats() {
        try {
            return ResponseEntity.ok(mcpServer.getSessionStats());
        } catch (Exception e) {
            log.error("获取会话统计失败", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to get session stats: " + e.getMessage()));
        }
    }

    /**
     * 重新加载MCP配置
     */
    @PostMapping("/reload")
    public ResponseEntity<Map<String, Object>> reloadConfiguration() {
        try {
            mcpServer.reloadConfiguration();
            List<String> serverTypes = mcpServer.getAvailableServerTypes();
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "MCP配置已重新加载",
                "availableServers", serverTypes,
                "count", serverTypes.size(),
                "timestamp", System.currentTimeMillis()
            ));
        } catch (Exception e) {
            log.error("重新加载配置失败", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to reload configuration: " + e.getMessage()));
        }
    }

    /**
     * 获取进程调试信息
     */
    @GetMapping("/debug/processes")
    public ResponseEntity<Map<String, Object>> getProcessDebugInfo() {
        try {
            return ResponseEntity.ok(mcpServer.getProcessDebugInfo());
        } catch (Exception e) {
            log.error("获取进程调试信息失败", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to get process debug info: " + e.getMessage()));
        }
    }

    /**
     * 检查端口冲突
     */
    @GetMapping("/debug/ports")
    public ResponseEntity<Map<String, Object>> checkPortConflicts() {
        try {
            Map<String, Object> portInfo = mcpServer.checkPortConflicts();
            return ResponseEntity.ok(portInfo);
        } catch (Exception e) {
            log.error("检查端口冲突失败", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to check port conflicts: " + e.getMessage()));
        }
    }

    /**
     * 获取启动状态
     */
    @GetMapping("/startup/status")
    public ResponseEntity<Map<String, Object>> getStartupStatus() {
        try {
            Map<String, Object> debugInfo = mcpServer.getProcessDebugInfo();
            
            @SuppressWarnings("unchecked")
            Map<String, Object> processServers = (Map<String, Object>) debugInfo.get("processServers");
            
            int totalProcesses = 0;
            int runningProcesses = 0;
            int failedProcesses = 0;
            
            if (processServers != null) {
                totalProcesses = processServers.size();
                for (Object serverInfo : processServers.values()) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> serverMap = (Map<String, Object>) serverInfo;
                    Boolean isRunning = (Boolean) serverMap.get("isRunning");
                    if (Boolean.TRUE.equals(isRunning)) {
                        runningProcesses++;
                    } else {
                        failedProcesses++;
                    }
                }
            }
            
            return ResponseEntity.ok(Map.of(
                "startupComplete", true,
                "totalProcesses", totalProcesses,
                "runningProcesses", runningProcesses,
                "failedProcesses", failedProcesses,
                "successRate", totalProcesses > 0 ? (runningProcesses * 100.0 / totalProcesses) : 0,
                "timestamp", System.currentTimeMillis(),
                "processDetails", processServers != null ? processServers : Map.of()
            ));
        } catch (Exception e) {
            log.error("获取启动状态失败", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to get startup status: " + e.getMessage()));
        }
    }

    /**
     * 强制清理所有进程和端口
     */
    @PostMapping("/cleanup/force")
    public ResponseEntity<Map<String, Object>> forceCleanup() {
        try {
            mcpServer.forceCleanup();
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "强制清理完成",
                "timestamp", System.currentTimeMillis()
            ));
        } catch (Exception e) {
            log.error("强制清理失败", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to force cleanup: " + e.getMessage()));
        }
    }
}

