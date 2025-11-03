package com.zhouruojun.travelingagent.tools.extractor;

/**
 * 工具结果提取器接口
 * 用于从工具执行的原始结果中提取关键信息，减少上下文冗余
 * 
 * 实现步骤：
 * 1. 实现此接口，添加@Component注解
 * 2. 在getSupportedToolName()中返回支持的工具名称（支持多个，逗号分隔）
 * 3. 在extract()方法中实现提取逻辑
 */
public interface ToolResultExtractor {
    
    /**
     * 获取支持的工具名称
     * 可以是单个工具名称，或多个工具名称（用逗号分隔）
     * 
     * @return 工具名称，例如："get_feed_detail" 或 "get_feed_detail,search_feeds"
     */
    String getSupportedToolName();
    
    /**
     * 提取工具执行结果
     * 
     * @param toolName 工具名称
     * @param rawResult 工具执行的原始结果（通常是JSON字符串）
     * @return 提取后的格式化结果（用于拼接到提示词中）
     */
    String extract(String toolName, String rawResult);
}

