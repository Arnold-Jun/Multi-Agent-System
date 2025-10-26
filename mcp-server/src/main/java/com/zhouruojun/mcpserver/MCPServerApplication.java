package com.zhouruojun.mcpserver;

import com.zhouruojun.mcpserver.mcp.MCPSessionManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * MCP服务器主应用类
 * 提供MCP协议代理服务，支持通过mcp.json动态配置MCP服务器
 */
@SpringBootApplication
@EnableScheduling
@Slf4j
public class MCPServerApplication implements CommandLineRunner {

    @Autowired
    private MCPSessionManager sessionManager;

    public static void main(String[] args) {
        log.info("启动MCP代理服务器...");
        SpringApplication.run(MCPServerApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        log.info("MCP服务器启动完成 - http://localhost:18083");
        
        // 启动前清理可能残留的端口占用
        cleanupResidualPorts();
        
        autoStartProcesses();
    }
    
    /**
     * 清理残留的端口占用
     */
    private void cleanupResidualPorts() {
        try {
            log.info("检查并清理残留的端口占用...");
            
            // 检查常用端口是否被占用
            int[] commonPorts = {18060, 18061, 18062, 18063, 18064, 18065, 18066, 18067, 18068, 18069, 18070};
            boolean hasOccupiedPorts = false;
            
            for (int port : commonPorts) {
                if (!isPortAvailable(port)) {
                    log.warn("端口 {} 被占用，可能是上次启动残留的进程", port);
                    hasOccupiedPorts = true;
                }
            }
            
            if (hasOccupiedPorts) {
                log.info("发现端口占用，尝试清理...");
                // 暂时只记录日志，实际清理在进程启动时进行
            } else {
                log.info("所有端口都可用");
            }
            
        } catch (Exception e) {
            log.error("清理残留端口时发生异常", e);
        }
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

    private void autoStartProcesses() {
        try {
            List<String> serverTypes = sessionManager.getNeedAutoStartServers();
            if (serverTypes.isEmpty()) {
                return;
            }
            
            log.info("启动 {} 个MCP进程", serverTypes.size());
            
            List<CompletableFuture<Void>> startupTasks = new java.util.ArrayList<>();
            
            for (String serverType : serverTypes) {
                CompletableFuture<Void> startupTask = CompletableFuture.runAsync(() -> {
                    try {
                        sessionManager.getOrCreateSession(serverType)
                                .thenCompose(session -> sessionManager.initializeSession(session))
                                .thenAccept(initialized -> {
                                    if (initialized) {
                                        log.info("启动成功: {}", serverType);
                                    } else {
                                        log.warn("启动失败: {}", serverType);
                                    }
                                })
                                .exceptionally(throwable -> {
                                    log.error("启动异常: {} - {}", serverType, throwable.getMessage());
                                    return null;
                                })
                                .join();
                    } catch (Exception e) {
                        log.error("启动异常: {}", serverType, e);
                    }
                });
                
                startupTasks.add(startupTask);
                Thread.sleep(2000);
            }
            
            CompletableFuture<Void> allTasks = CompletableFuture.allOf(
                startupTasks.toArray(new CompletableFuture[0])
            );
            
            try {
                allTasks.get(60, java.util.concurrent.TimeUnit.SECONDS);
                Thread.sleep(3000);
            } catch (java.util.concurrent.TimeoutException e) {
                log.warn("进程启动超时");
            }
            
        } catch (Exception e) {
            log.error("自动启动进程时发生异常", e);
        }
    }
}
