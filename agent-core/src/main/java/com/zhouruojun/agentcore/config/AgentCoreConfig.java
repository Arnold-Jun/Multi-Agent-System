package com.zhouruojun.agentcore.config;

import com.zhouruojun.agentcore.agent.SupervisorAgent;
import com.zhouruojun.agentcore.service.OllamaService;
import com.zhouruojun.agentcore.tools.A2AInvokeTool;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

import static dev.langchain4j.agent.tool.ToolSpecifications.toolSpecificationsFrom;

/**
 * Agent Core配置类
 * 配置主智能体和相关组件
 */
@Configuration
@Slf4j
public class AgentCoreConfig {

    @Autowired
    private OllamaConfig ollamaConfig;

    @Bean("agentCoreChatLanguageModel")
    public ChatLanguageModel chatLanguageModel(OllamaService ollamaService) {
        log.info("Configuring chat language model with Ollama: {}", ollamaConfig.getBaseUrl());
        
        try {
            ChatLanguageModel model = OpenAiChatModel.builder()
                    .baseUrl(ollamaConfig.getBaseUrl() + "/v1") // 确保使用正确的API路径
                    .apiKey("not-needed") // Ollama不需要API key
                    .modelName(ollamaConfig.getModel())
                    .timeout(Duration.ofMillis(ollamaConfig.getTimeout()))
                    .temperature(ollamaConfig.getTemperature())
                    .maxTokens(ollamaConfig.getMaxTokens())
                    .build();
            
            log.info("Successfully created ChatLanguageModel for Ollama");
            return model;
        } catch (Exception e) {
            log.error("Failed to create OpenAI-compatible ChatLanguageModel, using fallback", e);
            // 直接返回fallback实现，确保总是有可用的ChatLanguageModel
            return createFallbackChatLanguageModel(ollamaService);
        }
    }

    @Bean("agentCoreStreamingChatLanguageModel")
    public StreamingChatLanguageModel streamingChatLanguageModel() {
        log.info("Configuring streaming chat language model with Ollama: {}", ollamaConfig.getBaseUrl());
        
        try {
            return OpenAiStreamingChatModel.builder()
                    .baseUrl(ollamaConfig.getBaseUrl() + "/v1")
                    .apiKey("not-needed") // Ollama不需要API key
                    .modelName(ollamaConfig.getModel())
                    .timeout(Duration.ofMillis(ollamaConfig.getTimeout()))
                    .temperature(ollamaConfig.getTemperature())
                    .maxTokens(ollamaConfig.getMaxTokens())
                    .build();
        } catch (Exception e) {
            log.error("Failed to create StreamingChatLanguageModel", e);
            return null; // SupervisorAgent可以在没有streaming的情况下工作
        }
    }
    
    /**
     * 创建回退的ChatLanguageModel实现
     */
    @Bean("fallbackChatLanguageModel")
    public ChatLanguageModel createFallbackChatLanguageModel(OllamaService ollamaService) {
        return new ChatLanguageModel() {
            @Override
            public ChatResponse chat(ChatRequest chatRequest) {
                try {
                    // 提取消息内容
                    List<ChatMessage> messages = chatRequest.messages();
                    
                    // 构建上下文，包含系统提示和对话历史
                    StringBuilder contextBuilder = new StringBuilder();
                    contextBuilder.append("你是一名智能分发主管，负责分析用户需求并将任务分配给最合适的专业Agent。\n");
                    contextBuilder.append("你的团队由以下成员组成：data-analysis-agent\n");
                    contextBuilder.append("当用户询问数据分析相关问题时，你可以协调数据分析智能体来处理。\n\n");
                    
                    // 添加对话历史
                    for (ChatMessage message : messages) {
                        if (message instanceof dev.langchain4j.data.message.SystemMessage) {
                            // 跳过系统消息，我们已经有了自己的系统提示
                            continue;
                        } else if (message instanceof dev.langchain4j.data.message.UserMessage userMsg) {
                            contextBuilder.append("用户: ").append(userMsg.singleText()).append("\n");
                        } else if (message instanceof AiMessage aiMsg) {
                            contextBuilder.append("助手: ").append(aiMsg.text()).append("\n");
                        }
                    }
                    
                    contextBuilder.append("\n请根据上述对话历史和用户的最新需求进行回复：");
                    
                    log.info("Fallback ChatLanguageModel processing request with context length: {}", contextBuilder.length());
                    
                    // 使用OllamaService处理
                    String response = ollamaService.chat(contextBuilder.toString());
                    
                    AiMessage aiMessage = AiMessage.from(response);
                    return ChatResponse.builder()
                            .aiMessage(aiMessage)
                            .build();
                            
                } catch (Exception e) {
                    log.error("Fallback ChatLanguageModel failed", e);
                    AiMessage errorMessage = AiMessage.from(
                        "我是主智能体，负责协调多个专业智能体为您服务。我有完整的对话记忆，可以根据我们之前的对话为您提供帮助。当前正在处理您的请求，请稍等片刻。"
                    );
                    return ChatResponse.builder()
                            .aiMessage(errorMessage)
                            .build();
                }
            }
        };
    }

    @Bean
    public List<ToolSpecification> supervisorToolSpecs(A2AInvokeTool a2aInvokeTool) {
        log.info("Creating supervisor tool specifications");
        
        return toolSpecificationsFrom(a2aInvokeTool);
    }

    @Bean
    public SupervisorAgent supervisorAgent(
            OllamaService ollamaService,
            List<ToolSpecification> supervisorToolSpecs) {
        
        log.info("Creating supervisor agent");
        
        // 直接使用fallback ChatLanguageModel，确保稳定性
        ChatLanguageModel chatModel = createFallbackChatLanguageModel(ollamaService);
        
        return SupervisorAgent.supervisorBuilder()
                .chatLanguageModel(chatModel)
                .streamingChatLanguageModel(null) // 暂时不使用streaming
                .tools(supervisorToolSpecs)
                .agentName("supervisor-agent")
                .agentCards(null) // 将在运行时动态设置
                .build();
    }
}
