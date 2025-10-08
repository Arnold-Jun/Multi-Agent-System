package com.zhouruojun.jobsearchagent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Ollama配置类
 * 纯配置读取器，所有配置都从application.yml中读取
 */
@Data
@Component
@ConfigurationProperties(prefix = "ollama")
public class OllamaConfig {
    
    /**
     * Ollama服务基础URL
     */
    private String baseUrl;
    
    /**
     * 使用的模型名称
     */
    private String model;
    
    /**
     * 请求超时时间（毫秒）
     */
    private int timeout;
    
    /**
     * 温度参数，控制输出随机性
     */
    private double temperature;
    
    /**
     * 最大Token数量
     */
    private int maxTokens;
    
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
               && model != null && !model.trim().isEmpty()
               && timeout > 0
               && temperature >= 0.0 && temperature <= 2.0
               && maxTokens > 0;
    }
}
