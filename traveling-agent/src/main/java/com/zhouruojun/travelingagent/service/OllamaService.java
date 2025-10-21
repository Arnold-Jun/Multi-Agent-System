package com.zhouruojun.travelingagent.service;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.zhouruojun.travelingagent.config.OllamaConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Ollama服务工具类
 */
@Slf4j
@Service
public class OllamaService {

    @Autowired
    private OllamaConfig ollamaConfig;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    /**
     * 检查Ollama服务状态
     */
    public String checkOllamaStatus() {
        try {
            String url = ollamaConfig.getBaseUrl() + "/api/tags";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                JSONObject jsonResponse = JSONObject.parseObject(response.body());
                JSONArray models = jsonResponse.getJSONArray("models");
                if (models != null && !models.isEmpty()) {
                    return "✅ Ollama服务正常，可用模型数量: " + models.size();
                } else {
                    return "⚠️ Ollama服务正常，但未找到可用模型";
                }
            } else {
                return "❌ Ollama服务异常，状态码: " + response.statusCode();
            }
        } catch (Exception e) {
            log.error("检查Ollama状态失败", e);
            return "❌ Ollama服务不可用: " + e.getMessage();
        }
    }

    /**
     * 获取可用模型列表
     */
    public String getAvailableModels() {
        try {
            String url = ollamaConfig.getBaseUrl() + "/api/tags";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                JSONObject jsonResponse = JSONObject.parseObject(response.body());
                JSONArray models = jsonResponse.getJSONArray("models");
                
                if (models != null && !models.isEmpty()) {
                    StringBuilder result = new StringBuilder("可用模型列表:\n");
                    for (int i = 0; i < models.size(); i++) {
                        JSONObject model = models.getJSONObject(i);
                        String name = model.getString("name");
                        long size = model.getLongValue("size");
                        String modifiedAt = model.getString("modified_at");
                        result.append(String.format("- %s (大小: %.2f MB, 修改时间: %s)\n", 
                            name, size / 1024.0 / 1024.0, modifiedAt));
                    }
                    return result.toString();
                } else {
                    return "未找到可用模型";
                }
            } else {
                return "获取模型列表失败，状态码: " + response.statusCode();
            }
        } catch (Exception e) {
            log.error("获取模型列表失败", e);
            return "获取模型列表失败: " + e.getMessage();
        }
    }

    /**
     * 获取配置信息
     */
    public String getConfigInfo() {
        return String.format("""
            Ollama配置信息:
            - 服务地址: %s
            - 模型名称: %s
            - 超时时间: %d ms
            - 温度参数: %.2f
            - 最大Token: %d
            - 配置有效: %s
            """, 
            ollamaConfig.getBaseUrl(),
            ollamaConfig.getModel(),
            ollamaConfig.getTimeout(),
            ollamaConfig.getTemperature(),
            ollamaConfig.getMaxTokens(),
            ollamaConfig.isValid() ? "是" : "否"
        );
    }
}

