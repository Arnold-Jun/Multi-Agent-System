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
import java.util.List;
import java.util.Map;

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
                log.warn("mcp.json 配置文件不存在，使用空配置");
                configuration = new MCPConfiguration();
                configuration.setMcpServers(new HashMap<>());
                return;
            }

            try (InputStream inputStream = resource.getInputStream()) {
                configuration = objectMapper.readValue(inputStream, MCPConfiguration.class);
                
                if (configuration == null || configuration.getMcpServers() == null) {
                    log.warn("mcp.json 配置为空，使用空配置");
                    configuration = new MCPConfiguration();
                    configuration.setMcpServers(new HashMap<>());
                    return;
                }

                // 所有服务器默认启用（与Cursor对齐）
                Map<String, MCPServerInfo> enabledServers = configuration.getMcpServers();

                log.info("成功加载 MCP 配置，共 {} 个服务器（启用 {} 个）",
                        configuration.getMcpServers().size(), enabledServers.size());
                
                enabledServers.forEach((name, info) -> {
                    String connectionInfo;
                    if (info.isProcessMode()) {
                        connectionInfo = String.format("进程模式: %s", info.getProcessName());
                    } else {
                        connectionInfo = info.getUrl() != null ? info.getUrl() : "未配置URL";
                    }
                    log.info("  {} - {} ({})", name, info.getDescription(), connectionInfo);
                });

            }
        } catch (IOException e) {
            log.error("加载 mcp.json 配置失败", e);
            configuration = new MCPConfiguration();
            configuration.setMcpServers(new HashMap<>());
        }
    }

    /**
     * 获取所有启用的MCP服务器名称（与Cursor对齐，默认全部启用）
     */
    public Map<String, MCPServerInfo> getEnabledServers() {
        if (configuration == null || configuration.getMcpServers() == null) {
            return new HashMap<>();
        }
        
        return configuration.getMcpServers();
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
     * 检查服务器是否存在且启用（与Cursor对齐，默认全部启用）
     */
    public boolean isServerEnabled(String serverName) {
        MCPServerInfo info = getServerInfo(serverName);
        return info != null;
    }

    /**
     * 重新加载配置
     */
    public void reloadConfiguration() {
        log.info("重新加载 MCP 配置...");
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
     * MCP服务器信息（标准MCP配置）
     */
    @Data
    public static class MCPServerInfo {
        private String url;                    // MCP服务器URL
        private String description;             // 服务器描述
        private String command;                 // 进程启动命令（如npx, node, go等）
        private List<String> args;              // 命令参数
        private Map<String, String> env;        // 环境变量
        
        /**
         * 判断是否为进程模式
         * 通过command字段判断：有command字段表示进程模式
         */
        public boolean isProcessMode() {
            return command != null && !command.trim().isEmpty();
        }
        
        /**
         * 判断是否为HTTP模式
         */
        public boolean isHttpMode() {
            return url != null && !isProcessMode();
        }
        
        /**
         * 获取进程名称（用于进程管理）
         */
        public String getProcessName() {
            if (isProcessMode()) {
                return command + "_" + (args != null ? String.join("_", args) : "");
            }
            return null;
        }
        
        /**
         * 获取完整的启动命令
         */
        public String getFullCommand() {
            if (!isProcessMode()) {
                return null;
            }
            StringBuilder cmd = new StringBuilder(command);
            if (args != null && !args.isEmpty()) {
                cmd.append(" ").append(String.join(" ", args));
            }
            return cmd.toString();
        }
        
        /**
         * 获取工作目录（根据服务器类型智能选择）
         */
        public String getWorkingDirectory() {
            // 如果是Go程序，使用特定的工作目录
            if ("go".equals(command)) {
                return "C:\\Users\\ZhuanZ1\\mcp\\xiaohongshu-mcp";
            }
            // 其他情况使用用户主目录
            return System.getProperty("user.home");
        }
        
        /**
         * 判断是否需要自动启动（进程模式默认自动启动，混合模式需要启动本地进程）
         */
        public boolean isAutoStart() {
            // 进程模式：自动启动
            if (isProcessMode()) {
                return true;
            }
            // 混合模式：需要启动本地进程然后通过HTTP通信
            if (url != null && (url.contains("localhost:18060") || url.contains("localhost:18090"))) {
                return true;
            }
            return false;
        }
        
        /**
         * 获取启动延迟时间（默认3秒）
         */
        public int getStartupDelaySeconds() {
            return 3;
        }
        
        /**
         * 获取环境变量
         * 如果配置了env字段，返回配置的环境变量；否则返回空Map
         */
        public Map<String, String> getEnvironmentVariables() {
            return env != null ? env : new HashMap<>();
        }
    }
}

