package com.zhouruojun.dataanalysisagent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhouruojun.dataanalysisagent.config.OllamaConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Ollama管理服务
 * 专注于Ollama服务的状态检查和诊断功能
 * 不参与实际的AI推理流程（由LangChain4j处理）
 */
@Slf4j
@Service
public class OllamaService {
    
    @Autowired
    private OllamaConfig ollamaConfig;
    
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    
    public OllamaService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(60))
                .build();
        this.objectMapper = new ObjectMapper();
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
                JsonNode jsonNode = objectMapper.readTree(response.body());
                JsonNode models = jsonNode.get("models");
                
                if (models != null && models.isArray()) {
                    StringBuilder result = new StringBuilder("📋 可用模型列表:\n");
                    for (JsonNode model : models) {
                        String name = model.get("name").asText();
                        long size = model.get("size").asLong();
                        String modified = model.get("modified_at").asText();
                        result.append(String.format("  • %s (大小: %.2f GB, 修改时间: %s)\n", 
                            name, size / (1024.0 * 1024.0 * 1024.0), modified));
                    }
                    return result.toString();
                }
            }
            return "❌ 无法获取模型列表";
        } catch (Exception e) {
            log.error("获取模型列表失败", e);
            return "❌ 获取模型列表失败: " + e.getMessage();
        }
    }
    
    /**
     * 检查Ollama状态并返回详细信息
     */
    public String checkOllamaStatus() {
        try {
            if (!isAvailable()) {
                return "❌ Ollama服务不可用";
            }
            
            String models = getAvailableModels();
            return "✅ Ollama服务正常运行\n\n" + models;
        } catch (Exception e) {
            log.error("检查Ollama状态失败", e);
            return "❌ 检查Ollama状态失败: " + e.getMessage();
        }
    }
    
    /**
     * 获取Ollama配置信息
     */
    public String getConfigInfo() {
        return String.format("""
            🔧 Ollama配置信息:
            • 基础URL: %s
            • 当前模型: %s
            • 超时时间: %d ms
            • 温度参数: %.2f
            • 最大Token数: %d
            • 配置有效性: %s
            """, 
            ollamaConfig.getBaseUrl(),
            ollamaConfig.getModel(),
            ollamaConfig.getTimeout(),
            ollamaConfig.getTemperature(),
            ollamaConfig.getMaxTokens(),
            ollamaConfig.isValid() ? "✅ 有效" : "❌ 无效"
        );
    }
}
