package com.zhouruojun.agentcore.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Ollama测试服务
 * 提供Ollama服务的诊断和测试功能
 */
@Service
@Slf4j
public class OllamaTestService {

    @Autowired
    private OllamaService ollamaService;

    /**
     * 诊断Ollama服务
     */
    public String diagnose() {
        StringBuilder diagnosis = new StringBuilder();
        diagnosis.append("=== Ollama服务诊断 ===\n");
        
        try {
            // 检查服务可用性
            boolean isAvailable = ollamaService.isAvailable();
            diagnosis.append("服务状态: ").append(isAvailable ? "✅ 可用" : "❌ 不可用").append("\n");
            
            if (isAvailable) {
                // 获取可用模型
                String models = ollamaService.getAvailableModels();
                diagnosis.append("可用模型: ").append(models).append("\n");
                
                // 测试简单对话
                try {
                    String response = ollamaService.chat("Hello, this is a test message.");
                    diagnosis.append("测试对话: ✅ 成功\n");
                    diagnosis.append("响应长度: ").append(response.length()).append(" 字符\n");
                } catch (Exception e) {
                    diagnosis.append("测试对话: ❌ 失败 - ").append(e.getMessage()).append("\n");
                }
            } else {
                diagnosis.append("建议: 请检查Ollama服务是否已启动\n");
                diagnosis.append("启动命令: ollama serve\n");
            }
            
        } catch (Exception e) {
            diagnosis.append("诊断过程中发生错误: ").append(e.getMessage()).append("\n");
        }
        
        return diagnosis.toString();
    }

    /**
     * 测试Ollama连接
     */
    public boolean testConnection() {
        try {
            return ollamaService.isAvailable();
        } catch (Exception e) {
            log.error("Ollama connection test failed", e);
            return false;
        }
    }

    /**
     * 获取服务信息
     */
    public String getServiceInfo() {
        try {
            StringBuilder info = new StringBuilder();
            info.append("Ollama服务信息:\n");
            info.append("- 可用性: ").append(ollamaService.isAvailable() ? "是" : "否").append("\n");
            info.append("- 可用模型: ").append(ollamaService.getAvailableModels()).append("\n");
            return info.toString();
        } catch (Exception e) {
            return "获取服务信息失败: " + e.getMessage();
        }
    }
}
