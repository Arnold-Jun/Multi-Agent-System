package com.zhouruojun.dataanalysisagent.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class DataAnalysisConfig {

    @Autowired
    private OllamaConfig ollamaConfig;

    @Bean
    public ChatLanguageModel dataAnalysisChatLanguageModel() {
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