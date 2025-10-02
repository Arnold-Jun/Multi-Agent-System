package com.zhouruojun.dataanalysisagent.common;

import dev.langchain4j.data.message.AiMessage;
import lombok.extern.slf4j.Slf4j;

/**
 * 消息过滤器工具类
 * 用于过滤AI响应中的思考过程和markdown标记
 * 支持不同类型的消息过滤策略
 */
@Slf4j
public class MessageFilter {

    /**
     * 过滤掉思考过程内容和markdown-mark的文本
     * 移除<think>...</think>标签包裹的内容，以及markdown代码块标记
     * 虽然提示词已要求纯JSON输出，但保留过滤作为容错机制
     *
     * @param originalText 原始响应文本
     * @return 过滤后的文本
     */
    public static String filterThinkingContent(String originalText) {
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

    /**
     * 为主图节点创建过滤后的AI消息
     * 主图节点不应该有工具调用请求，只替换文本内容
     * 
     * @param originalAiMessage 原始AI消息
     * @return 过滤后的AI消息
     */
    public static AiMessage createMainGraphFilteredMessage(AiMessage originalAiMessage) {
        // 过滤掉思考过程，只保留实际响应
        String filteredText = filterThinkingContent(originalAiMessage.text());
        
        // 主图节点不应该有工具调用请求，只替换文本内容
        return AiMessage.from(filteredText);
    }

    /**
     * 为子图节点创建过滤后的AI消息
     * 子图节点支持工具调用，保留工具调用请求
     * 
     * @param originalAiMessage 原始AI消息
     * @return 过滤后的AI消息
     */
    public static AiMessage createSubGraphFilteredMessage(AiMessage originalAiMessage) {
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
     * 为子图节点创建过滤后的AI消息（支持工具调用）
     * 
     * @param originalAiMessage 原始AI消息
     * @return 过滤后的AI消息
     */
    public static AiMessage createSubGraphFilteredMessageWithTools(AiMessage originalAiMessage) {
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
}
