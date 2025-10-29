package com.zhouruojun.travelingagent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Qwen API配置类
 */
@Data
@Component
@ConfigurationProperties(prefix = "qwen")
public class QwenApiConfig {

    private String baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";
    private String apiKey = "sk-fae85a271d294b9b9d2309d69fd669b7";
    private String model = "qwen-plus";
    private int timeout = 120000;
    private double temperature = 0.7;
    private int maxTokens = 4096;
    private boolean enabled = false; // 默认禁用，需要手动启用

    /**
     * 检查配置是否有效
     */
    public boolean isValid() {
        return baseUrl != null && !baseUrl.trim().isEmpty()
            && apiKey != null && !apiKey.trim().isEmpty()
            && model != null && !model.trim().isEmpty()
            && timeout > 0
            && temperature >= 0.0 && temperature <= 2.0
            && maxTokens > 0;
    }
}
