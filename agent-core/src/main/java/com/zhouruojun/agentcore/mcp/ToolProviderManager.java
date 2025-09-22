package com.zhouruojun.agentcore.mcp;

import dev.langchain4j.agent.tool.ToolSpecification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工具提供者管理器
 * 管理智能体可用的工具规范
 * 
 */
@Slf4j
@Component
public class ToolProviderManager {
    
    private final Map<String, List<ToolSpecification>> userToolsCache = new ConcurrentHashMap<>();
    private final Map<String, List<ToolSpecification>> systemTools = new ConcurrentHashMap<>();
    
    /**
     * 加载工具提供者
     */
    public void loadToolProvider(List<ToolSpecification> userTools, String username, String requestId) {
        String cacheKey = username + "_" + requestId;
        
        List<ToolSpecification> allTools = new ArrayList<>();
        
        // 添加用户工具
        if (userTools != null) {
            allTools.addAll(userTools);
        }
        
        // 添加系统工具（如A2A调用工具等）
        allTools.addAll(getSystemTools());
        
        userToolsCache.put(cacheKey, allTools);
        log.info("Loaded {} tools for user {} request {}", allTools.size(), username, requestId);
    }
    
    /**
     * 获取工具规范
     */
    public List<ToolSpecification> toolSpecifications(String username, String requestId) {
        String cacheKey = username + "_" + requestId;
        return userToolsCache.getOrDefault(cacheKey, getSystemTools());
    }
    
    /**
     * 获取系统工具
     */
    private List<ToolSpecification> getSystemTools() {
        return systemTools.getOrDefault("system", new ArrayList<>());
    }
    
    /**
     * 注册系统工具
     */
    public void registerSystemTool(ToolSpecification toolSpec) {
        systemTools.computeIfAbsent("system", k -> new ArrayList<>()).add(toolSpec);
        log.info("Registered system tool: {}", toolSpec.name());
    }
    
    /**
     * 清除用户工具缓存
     */
    public void clearUserTools(String username, String requestId) {
        String cacheKey = username + "_" + requestId;
        userToolsCache.remove(cacheKey);
        log.info("Cleared tools cache for user {} request {}", username, requestId);
    }
    
    /**
     * 获取所有工具数量
     */
    public int getToolCount(String username, String requestId) {
        return toolSpecifications(username, requestId).size();
    }
}