package com.zhouruojun.mcpserver.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * MCP配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "mcp.tools")
public class MCPProperties {
    
    private AmapConfig amap;
    private XiaohongshuConfig xiaohongshu;
    private WeatherConfig weather;
    
    @Data
    public static class AmapConfig {
        private String key;
        private String baseUrl;
        private String mcpUrl;
    }
    
    @Data
    public static class XiaohongshuConfig {
        private String baseUrl;
        private String mcpUrl;
        private String description;
    }
    
    @Data
    public static class WeatherConfig {
        private String apiKey;
        private String baseUrl;
    }
}
