package com.zhouruojun.agentcore.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Ollama配置类
 */
@Data
@Component
@ConfigurationProperties(prefix = "ollama")
public class OllamaConfig {
    
    private String baseUrl = "http://localhost:11434";
    private String model = "qwen3:8b";
    private int timeout = 120000;
    private double temperature = 0.7;
    private int maxTokens = 4096;
    
    /**
     * 获取完整的API URL
     */
    public String getApiUrl() {
        return baseUrl + "/api/generate";
    }
    
    /**
     * 获取聊天API URL
     */
    public String getChatApiUrl() {
        return baseUrl + "/api/chat";
    }
    
    /**
     * 检查配置是否有效
     */
    public boolean isValid() {
        return baseUrl != null && !baseUrl.trim().isEmpty() 
               && model != null && !model.trim().isEmpty();
    }
}
