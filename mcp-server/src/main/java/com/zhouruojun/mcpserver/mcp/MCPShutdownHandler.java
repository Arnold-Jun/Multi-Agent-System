package com.zhouruojun.mcpserver.mcp;

import org.springframework.lang.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

/**
 * MCP进程关闭处理器
 * 在应用关闭时优雅地停止所有MCP进程
 * 提供Spring Context关闭和JVM关闭钩子双重保障
 */
@Slf4j
@Component
public class MCPShutdownHandler implements ApplicationListener<ContextClosedEvent> {

    @Autowired
    private MCPProcessManager processManager;
    
    private volatile boolean shutdownExecuted = false;

    @PostConstruct
    public void init() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (!shutdownExecuted) {
                executeShutdown();
            }
        }, "MCP-Shutdown-Hook"));
    }

    @Override
    public void onApplicationEvent(@NonNull ContextClosedEvent event) {
        executeShutdown();
    }
    
    @PreDestroy
    public void preDestroy() {
        executeShutdown();
    }
    
    /**
     * 执行关闭逻辑（确保只执行一次）
     */
    private synchronized void executeShutdown() {
        if (shutdownExecuted) {
            return;
        }
        
        shutdownExecuted = true;
        
        try {
            log.info("开始关闭MCP服务器，清理所有进程和端口...");
            
            processManager.stopAllProcesses();
            processManager.forceCleanupAllPorts();
            
            log.info("MCP服务器关闭完成");
        } catch (Exception e) {
            log.error("停止MCP进程异常", e);
        }
    }
}
