package com.zhouruojun.mcpserver.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * MCP服务器配置加载器
 * 从mcp.json文件加载MCP服务器配置
 */
@Slf4j
@Component
public class MCPServerConfig {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MCPConfiguration configuration;

    @PostConstruct
    public void loadConfiguration() {
        try {
            Resource resource = new ClassPathResource("mcp.json");
            
            if (!resource.exists()) {
                log.warn("⚠️ mcp.json 配置文件不存在，使用空配置");
                configuration = new MCPConfiguration();
                configuration.setMcpServers(new HashMap<>());
                return;
            }

            try (InputStream inputStream = resource.getInputStream()) {
                configuration = objectMapper.readValue(inputStream, MCPConfiguration.class);
                
                if (configuration == null || configuration.getMcpServers() == null) {
                    log.warn("⚠️ mcp.json 配置为空，使用空配置");
                    configuration = new MCPConfiguration();
                    configuration.setMcpServers(new HashMap<>());
                    return;
                }

                // 过滤掉未启用的服务器
                Map<String, MCPServerInfo> enabledServers = configuration.getMcpServers().entrySet().stream()
                        .filter(entry -> entry.getValue().isEnabled())
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

                log.info("✅ 成功加载 MCP 配置，共 {} 个服务器（启用 {} 个）", 
                        configuration.getMcpServers().size(), enabledServers.size());
                
                enabledServers.forEach((name, info) -> {
                    log.info("  📡 {} - {} ({})", name, info.getDescription(), info.getUrl());
                });

            }
        } catch (IOException e) {
            log.error("❌ 加载 mcp.json 配置失败", e);
            configuration = new MCPConfiguration();
            configuration.setMcpServers(new HashMap<>());
        }
    }

    /**
     * 获取所有启用的MCP服务器名称
     */
    public Map<String, MCPServerInfo> getEnabledServers() {
        if (configuration == null || configuration.getMcpServers() == null) {
            return new HashMap<>();
        }
        
        return configuration.getMcpServers().entrySet().stream()
                .filter(entry -> entry.getValue().isEnabled())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * 根据服务器名称获取配置
     */
    public MCPServerInfo getServerInfo(String serverName) {
        if (configuration == null || configuration.getMcpServers() == null) {
            return null;
        }
        return configuration.getMcpServers().get(serverName);
    }

    /**
     * 检查服务器是否存在且启用
     */
    public boolean isServerEnabled(String serverName) {
        MCPServerInfo info = getServerInfo(serverName);
        return info != null && info.isEnabled();
    }

    /**
     * 重新加载配置
     */
    public void reloadConfiguration() {
        log.info("🔄 重新加载 MCP 配置...");
        loadConfiguration();
    }

    /**
     * MCP配置根对象
     */
    @Data
    public static class MCPConfiguration {
        private Map<String, MCPServerInfo> mcpServers;
    }

    /**
     * MCP服务器信息
     */
    @Data
    public static class MCPServerInfo {
        private String url;
        private String description;
        private boolean enabled = true; // 默认启用
    }
}

