package com.zhouruojun.agentcore.common;

import lombok.extern.slf4j.Slf4j;

/**
 * 内容过滤器工具类
 * 提供统一的内容过滤功能
 */
@Slf4j
public class ContentFilter {
    
    /**
     * 过滤思考内容
     * 移除<think>标签、markdown代码块标记等
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
        
        return filteredText;
    }
}
