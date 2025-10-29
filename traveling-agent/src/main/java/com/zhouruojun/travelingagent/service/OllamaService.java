package com.zhouruojun.travelingagent.service;

import com.zhouruojun.travelingagent.config.OllamaConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Ollama服务工具类
 * 仅保留配置信息获取功能，删除状态检查功能
 */
@Slf4j
@Service
public class OllamaService {

    @Autowired
    private OllamaConfig ollamaConfig;

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

