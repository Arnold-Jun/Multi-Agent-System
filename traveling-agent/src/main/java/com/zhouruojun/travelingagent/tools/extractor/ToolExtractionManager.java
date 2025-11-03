package com.zhouruojun.travelingagent.tools.extractor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * 工具结果提取管理器
 * 精简实现：只管理工具名称到提取函数的映射
 */
@Slf4j
@Component
public class ToolExtractionManager {
    
    /**
     * 工具名称 -> 提取函数
     * 如果没有注册提取函数，则返回原始结果
     */
    private final Map<String, Function<String, String>> extractors = new ConcurrentHashMap<>();
    
    /**
     * 自动注入所有提取器
     * Spring会自动发现所有实现了ToolResultExtractor接口的Bean
     */
    @Autowired(required = false)
    private List<ToolResultExtractor> toolExtractors = new ArrayList<>();
    
    /**
     * 初始化提取器注册表
     */
    @PostConstruct
    public void init() {
        log.info("初始化工具结果提取管理器...");
        
        // 注册所有自动发现的提取器
        for (ToolResultExtractor extractor : toolExtractors) {
            String supportedTools = extractor.getSupportedToolName();
            if (supportedTools == null || supportedTools.isEmpty() || "*".equals(supportedTools.trim())) {
                continue;
            }
            
            // 解析支持的工具列表（支持逗号分隔）
            String[] tools = supportedTools.split(",");
            for (String toolName : tools) {
                final String finalToolName = toolName.trim();
                if (!finalToolName.isEmpty()) {
                    extractors.put(finalToolName, (result) -> extractor.extract(finalToolName, result));
                    log.debug("注册提取器 {} 用于工具 {}", extractor.getClass().getSimpleName(), finalToolName);
                }
            }
        }
        
        log.info("工具结果提取管理器初始化完成，已注册 {} 个工具的提取器", extractors.size());
    }
    
    /**
     * 提取工具结果
     * 
     * @param toolName 工具名称
     * @param rawResult 原始结果
     * @return 提取后的结果，如果没有注册提取器则返回原始结果
     */
    public String extract(String toolName, String rawResult) {
        if (toolName == null || toolName.trim().isEmpty() || rawResult == null) {
            return rawResult;
        }
        
        Function<String, String> extractor = extractors.get(toolName.trim());
        if (extractor == null) {
            // 没有注册提取器，返回原始结果
            return rawResult;
        }
        
        try {
            String extracted = extractor.apply(rawResult);
            log.debug("工具 {} 使用了提取器，结果长度: {} -> {}", 
                toolName, 
                rawResult.length(),
                extracted != null ? extracted.length() : 0);
            return extracted;
        } catch (Exception e) {
            log.warn("提取工具 {} 结果时发生错误，返回原始结果: {}", toolName, e.getMessage());
            return rawResult;
        }
    }
    
    /**
     * 检查工具是否注册了专用提取器
     */
    public boolean hasCustomExtractor(String toolName) {
        return toolName != null && extractors.containsKey(toolName.trim());
    }
    
    /**
     * 从MCP的JSON-RPC响应格式中提取实际的工具结果
     * 
     * @param rawResult 原始响应
     * @param toolName 工具名称（用于日志）
     * @return 提取的实际工具结果
     */
    public String extractFromJsonRpc(String rawResult, String toolName) {
        if (rawResult == null || rawResult.trim().isEmpty()) {
            return rawResult;
        }
        
        String str = rawResult.trim();

        // 处理 text={ 的情况（JSON对象）
        int textPos = str.indexOf("text={");
        if (textPos >= 0) {
            int jsonStart = textPos + 5;
            int braceCount = 1;
            int jsonEnd = jsonStart;
            
            for (int i = jsonStart + 1; i < str.length() && braceCount > 0; i++) {
                char ch = str.charAt(i);
                if (ch == '{') {
                    braceCount++;
                } else if (ch == '}') {
                    braceCount--;
                    if (braceCount == 0) {
                        jsonEnd = i + 1;
                        break;
                    }
                }
            }
            
            if (jsonEnd > jsonStart) {
                return str.substring(jsonStart, jsonEnd);
            }
        }
        
        // 处理 text=" 的情况（字符串类型）
        textPos = str.indexOf("text=\"");
        if (textPos >= 0) {
            int valueStart = textPos + 6;
            int endQuote = str.indexOf('"', valueStart);
            if (endQuote > valueStart) {
                return str.substring(valueStart, endQuote);
            }
        }
        
        // 提取失败，返回原始结果
        log.warn("工具 {} 无法从响应中提取text字段，返回原始结果", toolName);
        return rawResult;
    }
    
    /**
     * 处理工具执行结果（完整流程）
     * 1. 从MCP的JSON-RPC格式中提取实际的工具结果（如果存在）
     * 2. 使用自定义提取器进行处理（如果有注册）
     * 
     * @param toolName 工具名称
     * @param rawResult MCP返回的原始结果（可能是JSON-RPC格式或直接的工具结果）
     * @return 处理后的结果
     */
    public String processToolResult(String toolName, String rawResult) {
        if (rawResult == null || rawResult.trim().isEmpty()) {
            return rawResult;
        }
        
        // 步骤1：从JSON-RPC格式中提取实际的工具结果
        String actualResult = extractFromJsonRpc(rawResult, toolName);
        
        // 步骤2：使用自定义提取器处理结果（如果有）
        return extract(toolName, actualResult);
    }
}

