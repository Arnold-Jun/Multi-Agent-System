package com.zhouruojun.dataanalysisagent.agent.actions;

import com.zhouruojun.dataanalysisagent.agent.BaseAgent;
import com.zhouruojun.dataanalysisagent.agent.state.BaseAgentState;
import dev.langchain4j.data.message.AiMessage;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.bsc.async.AsyncGenerator;
import org.bsc.langgraph4j.action.NodeAction;
import org.bsc.langgraph4j.streaming.StreamingOutput;

import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;

/**
 * 调用数据分析智能体的节点
 * 负责调用数据分析智能体进行推理
 */
@Slf4j
public class CallAgent<T extends BaseAgentState> implements NodeAction<T> {

    /**
     * 智能体名称
     */
    final String agentName;

    /**
     * 智能体实例
     */
    final BaseAgent agent;

    /**
     * 流式输出队列
     */
    @SuppressWarnings("unused")
    private BlockingQueue<AsyncGenerator.Data<StreamingOutput<T>>> queue;

    /**
     * 构造函数
     *
     * @param agentName 智能体名称
     * @param agent 智能体实例
     */
    public CallAgent(@NonNull String agentName, @NonNull BaseAgent agent) {
        this.agentName = agentName;
        this.agent = agent;
    }

    /**
     * 设置流式输出队列
     *
     * @param queue 队列
     */
    public void setQueue(BlockingQueue<AsyncGenerator.Data<StreamingOutput<T>>> queue) {
        this.queue = queue;
    }

    /**
     * 应用智能体调用逻辑
     *
     * @param state 当前状态
     * @return 包含智能体响应的映射
     */
    @Override
    public Map<String, Object> apply(T state) {
        log.info("Calling agent: {}", agentName);

        return applyWithRetry(state);
    }

    /**
     * 带重试机制的智能体调用
     */
    private Map<String, Object> applyWithRetry(T state) {
        // 根据智能体类型设置不同的重试策略
        int maxRetries = getMaxRetries();
        int retryCount = 0;
        Exception lastException = null;

        while (retryCount < maxRetries) {
            try {
                // 获取消息列表
                var messages = state.messages();

                // 调用智能体
                var response = agent.execute(messages);

                // 获取AI消息
                AiMessage aiMessage = response.aiMessage();
                
                // 创建过滤后的AI消息，保留原始消息的所有属性，只替换文本内容
                AiMessage filteredAiMessage = createFilteredAiMessage(aiMessage);

                // 检查是否有工具调用请求
                if (aiMessage.hasToolExecutionRequests()) {
                    log.info("Agent {} requested tool execution: {}", agentName, aiMessage.toolExecutionRequests());
                    return Map.of(
                            "messages", List.of(filteredAiMessage),
                            "toolExecutionRequests", aiMessage.toolExecutionRequests()
                    );
                } else {
                    // 对于没有工具调用的响应，直接返回finalResponse
                    log.info("Agent {} responded: {}", agentName, filteredAiMessage.text());
                    return Map.of(
                            "messages", List.of(filteredAiMessage),
                            "finalResponse", filteredAiMessage.text()
                    );
                }

            } catch (Exception e) {
                lastException = e;
                retryCount++;
                log.warn("Agent {} 调用失败，重试 {}/{}: {}", agentName, retryCount, maxRetries, e.getMessage());
                
                if (retryCount < maxRetries) {
                    try {
                        Thread.sleep(1000L * retryCount); // 递增延迟
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        // 重试失败，抛出异常
        log.error("Agent {} 调用失败，已达到最大重试次数 {}", agentName, maxRetries, lastException);
        throw new RuntimeException("Failed to call agent: " + agentName + " after " + maxRetries + " retries", lastException);
    }
    
    /**
     * 根据智能体类型获取最大重试次数
     */
    private int getMaxRetries() {
        return switch (agentName) {
            case "scheduler" -> Integer.MAX_VALUE; // Scheduler无限重试
            case "planner" -> 3; // Planner重试3次
            case "summary" -> 3; // Summary重试3次
            default -> 3; // 其他智能体重试3次
        };
    }
    
    /**
     * 创建过滤后的AI消息，保留原始消息的所有属性，只替换文本内容
     * 
     * @param originalAiMessage 原始AI消息
     * @return 过滤后的AI消息
     */
    private AiMessage createFilteredAiMessage(AiMessage originalAiMessage) {
        // 过滤掉思考过程，只保留实际响应
        String filteredText = filterThinkingContent(originalAiMessage.text());
        
        // 创建新的AI消息，保留原始消息的所有属性，只替换文本内容
        if (originalAiMessage.hasToolExecutionRequests()) {
            // 如果有工具调用请求，保留工具调用请求
            return AiMessage.from(filteredText, originalAiMessage.toolExecutionRequests());
        } else {
            // 如果没有工具调用请求，只替换文本内容
            return AiMessage.from(filteredText);
        }
    }
    
    /**
     * 过滤掉思考过程内容和markdown标记
     * 移除<think>...</think>标签包裹的内容，以及markdown代码块标记
     * 虽然提示词已要求纯JSON输出，但保留过滤作为容错机制
     *
     * @param originalText 原始响应文本
     * @return 过滤后的文本
     */
    private String filterThinkingContent(String originalText) {
        if (originalText == null || originalText.trim().isEmpty()) {
            return originalText;
        }
        
        String filteredText = originalText;
        
        // 1. 移除<think>...</think>标签及其内容（使用更精确的正则表达式）
        // 使用DOTALL模式匹配换行符，使用非贪婪匹配
        filteredText = filteredText.replaceAll("(?s)<think>.*?</think>", "");
        
        // 2. 移除单独的<think>标签（如果没有闭合标签）
        filteredText = filteredText.replaceAll("(?s)<think>.*", "");
        
        // 3. 移除markdown代码块标记（容错处理）
        // 移除开头的```json或```标记
        filteredText = filteredText.replaceAll("^\\s*```(?:json)?\\s*", "");
        
        // 移除结尾的```标记
        filteredText = filteredText.replaceAll("\\s*```\\s*$", "");
        
        // 4. 清理多余的空白字符和换行
        filteredText = filteredText.trim();
        
        // 5. 如果过滤后为空，返回原始文本
        if (filteredText.isEmpty()) {
            log.warn("Filtered text is empty, returning original text");
            return originalText;
        }
        
        // 6. 记录过滤信息（仅在调试模式下）
        if (log.isDebugEnabled()) {
            log.debug("Original text length: {}, Filtered text length: {}", 
                     originalText.length(), filteredText.length());
            log.debug("Original text: {}", originalText);
            log.debug("Filtered text: {}", filteredText);
        }
        
        return filteredText;
    }
}