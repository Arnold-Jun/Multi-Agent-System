package com.zhouruojun.agentcore.service;

import com.alibaba.fastjson.JSON;
import com.zhouruojun.agentcore.config.OllamaConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * Ollama服务类
 */
@Slf4j
@Service
public class OllamaService {
    
    private final OllamaConfig ollamaConfig;
    private final HttpClient httpClient;
    
    public OllamaService(OllamaConfig ollamaConfig) {
        this.ollamaConfig = ollamaConfig;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(ollamaConfig.getTimeout()))
                .build();
    }
    
    /**
     * 发送聊天请求到Ollama
     */
    public String chat(String message) {
        try {
            Map<String, Object> requestBody = Map.of(
                "model", ollamaConfig.getModel(),
                "messages", new Object[]{
                    Map.of("role", "user", "content", message)
                },
                "stream", false,
                "temperature", ollamaConfig.getTemperature(),
                "num_predict", ollamaConfig.getMaxTokens()
            );
            
            String jsonBody = JSON.toJSONString(requestBody);
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ollamaConfig.getChatApiUrl()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .timeout(Duration.ofMillis(ollamaConfig.getTimeout()))
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, 
                    HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                Map<String, Object> responseMap = JSON.parseObject(response.body());
                Map<String, Object> messageObj = (Map<String, Object>) responseMap.get("message");
                return (String) messageObj.get("content");
            } else {
                log.error("Ollama API error: {} - {}", response.statusCode(), response.body());
                return "抱歉，AI服务暂时不可用，请稍后重试。";
            }
            
        } catch (Exception e) {
            log.error("Error calling Ollama API", e);
            return "抱歉，调用AI服务时出现错误：" + e.getMessage();
        }
    }
    
    /**
     * 检查Ollama服务是否可用
     */
    public boolean isAvailable() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ollamaConfig.getBaseUrl() + "/api/tags"))
                    .GET()
                    .timeout(Duration.ofSeconds(5))
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, 
                    HttpResponse.BodyHandlers.ofString());
            
            return response.statusCode() == 200;
        } catch (Exception e) {
            log.warn("Ollama service not available: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * 获取可用模型列表
     */
    public String getAvailableModels() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ollamaConfig.getBaseUrl() + "/api/tags"))
                    .GET()
                    .timeout(Duration.ofSeconds(5))
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, 
                    HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                return response.body();
            }
        } catch (Exception e) {
            log.error("Error getting models", e);
        }
        return "[]";
    }
}
