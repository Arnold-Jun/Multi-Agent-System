package com.zhouruojun.travelingagent.config;

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
//    private String model = "qwen3:8b";
    private String model = "gpt-oss:20b-cloud";
    private int timeout = 120000;
    private double temperature = 0.7;
    private int maxTokens = 4096;
    
    /**
     * 检查配置是否有效
     */
    public boolean isValid() {
        return baseUrl != null && !baseUrl.trim().isEmpty() 
            && model != null && !model.trim().isEmpty()
            && timeout > 0 
            && temperature >= 0.0 && temperature <= 2.0
            && maxTokens > 0;
    }
}

