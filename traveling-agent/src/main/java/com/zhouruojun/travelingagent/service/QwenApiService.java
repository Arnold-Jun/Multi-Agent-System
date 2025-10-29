package com.zhouruojun.travelingagent.service;

import com.zhouruojun.travelingagent.config.QwenApiConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Qwen API服务
 * 提供与Qwen API的交互功能
 */
@Slf4j
@Service
public class QwenApiService {

    @Autowired
    private QwenApiConfig qwenApiConfig;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 检查Qwen API状态
     */
    public String checkQwenApiStatus() {
        if (!qwenApiConfig.isEnabled()) {
            return "Qwen API未启用";
        }

        if (!qwenApiConfig.isValid()) {
            return "Qwen API配置无效";
        }

        try {
            // 发送一个简单的测试请求
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", qwenApiConfig.getModel());
            requestBody.put("messages", List.of(
                Map.of("role", "user", "content", "Hello")
            ));
            requestBody.put("max_tokens", 10);
            requestBody.put("enable_thinking", false);  // 修复Qwen API参数错误

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + qwenApiConfig.getApiKey());

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            @SuppressWarnings("rawtypes")
            ResponseEntity<Map> response = restTemplate.postForEntity(
                qwenApiConfig.getBaseUrl() + "/chat/completions",
                entity,
                Map.class
            );

            if (response.getStatusCode() == HttpStatus.OK) {
                return "Qwen API连接正常";
            } else {
                return "Qwen API连接异常: " + response.getStatusCode();
            }
        } catch (Exception e) {
            log.error("检查Qwen API状态失败", e);
            return "Qwen API连接失败: " + e.getMessage();
        }
    }

    /**
     * 获取Qwen API配置信息
     */
    public String getQwenApiInfo() {
        if (!qwenApiConfig.isEnabled()) {
            return "Qwen API未启用";
        }

        return String.format("""
            Qwen API配置信息:
            - Base URL: %s
            - Model: %s
            - API Key: %s
            - Timeout: %d ms
            - Temperature: %.2f
            - Max Tokens: %d
            - Enabled: %s
            """,
            qwenApiConfig.getBaseUrl(),
            qwenApiConfig.getModel(),
            qwenApiConfig.getApiKey().substring(0, 10) + "...",
            qwenApiConfig.getTimeout(),
            qwenApiConfig.getTemperature(),
            qwenApiConfig.getMaxTokens(),
            qwenApiConfig.isEnabled()
        );
    }

    /**
     * 检查是否启用Qwen API
     */
    public boolean isEnabled() {
        return qwenApiConfig.isEnabled() && qwenApiConfig.isValid();
    }

    /**
     * 获取配置对象
     */
    public QwenApiConfig getConfig() {
        return qwenApiConfig;
    }
}
