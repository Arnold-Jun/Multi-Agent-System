package com.zhouruojun.jobsearchagent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Boss直聘Mock API配置
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "boss.api")
public class BossApiConfig {
    
    /**
     * Boss API基础URL
     */
    private String baseUrl = "http://localhost:8084/api";
    
    /**
     * 连接超时时间（毫秒）
     */
    private int connectTimeout = 10000;
    
    /**
     * 读取超时时间（毫秒）
     */
    private int readTimeout = 30000;
    
    /**
     * 是否启用Boss API
     */
    private boolean enabled = true;
    
    /**
     * 重试次数
     */
    private int retryCount = 3;
    
    /**
     * 重试间隔（毫秒）
     */
    private int retryInterval = 1000;
}
