package com.zhouruojun.amadeusmcp.config;

import com.amadeus.Amadeus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Amadeus SDK配置类
 */
@Configuration
@Slf4j
public class AmadeusConfig {

    @Value("${amadeus.api-key:StU1vYrS5QFb1he9af9vAFi16BA6ErDF}")
    private String apiKey;

    @Value("${amadeus.api-secret:04keLpVKBHzpAD8Z}")
    private String apiSecret;

    @Bean
    public Amadeus amadeus() {
        try {
            log.info("初始化Amadeus SDK，API Key: {}", apiKey.substring(0, Math.min(apiKey.length(), 8)) + "...");
            log.info("API Secret: {}", apiSecret.substring(0, Math.min(apiSecret.length(), 8)) + "...");
            
            // 使用测试环境
            Amadeus amadeus = Amadeus.builder(apiKey, apiSecret).build();
            log.info("Amadeus SDK初始化成功，环境: test");
            return amadeus;
        } catch (Exception e) {
            log.error("Amadeus SDK初始化失败", e);
            throw new RuntimeException("Amadeus SDK初始化失败: " + e.getMessage(), e);
        }
    }
}
