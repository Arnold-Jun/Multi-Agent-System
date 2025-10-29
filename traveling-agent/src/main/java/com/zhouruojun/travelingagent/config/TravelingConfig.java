package com.zhouruojun.travelingagent.config;

import com.zhouruojun.travelingagent.service.QwenApiService;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Slf4j
@Configuration
public class TravelingConfig {

    @Autowired
    private OllamaConfig ollamaConfig;

    @Autowired
    private QwenApiService qwenApiService;

    @Bean
    public ChatLanguageModel travelingChatLanguageModel() {
        // 优先使用Qwen API，如果未启用则使用Ollama
        if (qwenApiService.isEnabled()) {
            log.info("使用Qwen API进行推理");
            return createQwenChatModel();
        } else {
            log.info("使用Ollama进行推理");
            return createOllamaChatModel();
        }
    }

    /**
     * 创建Qwen API ChatLanguageModel
     */
    private ChatLanguageModel createQwenChatModel() {
        var config = qwenApiService.getConfig();
        // 使用自定义的QwenChatModel来解决enable_thinking参数问题
        return new com.zhouruojun.travelingagent.service.QwenChatModel(config);
    }

    /**
     * 创建Ollama ChatLanguageModel
     */
    private ChatLanguageModel createOllamaChatModel() {
        return OpenAiChatModel.builder()
                .baseUrl(ollamaConfig.getBaseUrl() + "/v1")
                .apiKey("not-needed") // Ollama不需要API key
                .modelName(ollamaConfig.getModel())
                .timeout(Duration.ofMillis(ollamaConfig.getTimeout()))
                .temperature(ollamaConfig.getTemperature())
                .maxTokens(ollamaConfig.getMaxTokens())
                .logRequests(true)
                .logResponses(true)
                .build();
    }
}





