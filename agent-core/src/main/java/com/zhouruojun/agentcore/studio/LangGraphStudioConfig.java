package com.zhouruojun.agentcore.studio;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * LangGraph Studio配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "langgraph.studio")
public class LangGraphStudioConfig {
    
    private boolean enabled = true;
    private String baseUrl = "http://localhost:8123";
    private String apiKey;
    private int maxRetries = 3;
    private int timeout = 120000;
    
    /**
     * 检查配置是否有效
     */
    public boolean isValid() {
        return enabled && baseUrl != null && !baseUrl.trim().isEmpty();
    }
}
