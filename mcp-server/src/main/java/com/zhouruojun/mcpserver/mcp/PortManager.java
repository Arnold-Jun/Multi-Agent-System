package com.zhouruojun.mcpserver.mcp;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 端口管理器
 * 管理MCP进程使用的端口分配和释放
 */
@Slf4j
@Component
public class PortManager {

    private final Set<Integer> allocatedPorts = ConcurrentHashMap.newKeySet();
    private final int[] commonPorts = {18060, 18061, 18062, 18063, 18064, 18065, 18066, 18067, 18068, 18069, 18070};

    /**
     * 获取可用端口
     */
    public int getAvailablePort() {
        for (int port : commonPorts) {
            if (isPortAvailable(port)) {
                allocatedPorts.add(port);
                log.debug("分配端口: {}", port);
                return port;
            }
        }
        throw new RuntimeException("没有可用的端口");
    }

    /**
     * 释放端口
     */
    public void releasePort(int port) {
        allocatedPorts.remove(port);
        log.debug("释放端口: {}", port);
    }

    /**
     * 检查端口是否可用
     */
    public boolean isPortAvailable(int port) {
        try (java.net.ServerSocket serverSocket = new java.net.ServerSocket(port)) {
            return true;
        } catch (java.io.IOException e) {
            return false;
        }
    }

    /**
     * 清理所有端口
     */
    public void clearAllPorts() {
        allocatedPorts.clear();
        log.debug("清理所有端口");
    }
}
