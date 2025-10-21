package com.zhouruojun.travelingagent.mcp;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工具注册表
 * 管理工具与智能体的映射关系
 */
@Slf4j
@Component
public class ToolRegistry {
    
    // 工具名 -> 支持的智能体列表
    private final Map<String, Set<String>> toolAgentMap = new ConcurrentHashMap<>();
    
    // 智能体 -> 工具列表
    private final Map<String, Set<String>> agentToolMap = new ConcurrentHashMap<>();
    
    /**
     * 注册工具与智能体的关系
     */
    public void registerTool(String toolName, String... agentNames) {
        Set<String> agents = new HashSet<>(Arrays.asList(agentNames));
        toolAgentMap.put(toolName, agents);
        
        // 反向映射
        for (String agentName : agentNames) {
            agentToolMap.computeIfAbsent(agentName, (@SuppressWarnings("unused") var k) -> new HashSet<>()).add(toolName);
        }
    }
    
    /**
     * 从注解自动注册工具
     */
    public void registerToolFromAnnotation(String toolName, Method method) {
        ToolCategory annotation = method.getAnnotation(ToolCategory.class);
        if (annotation != null && annotation.agents().length > 0) {
            registerTool(toolName, annotation.agents());
        }
    }
    
    /**
     * 获取工具支持的智能体列表
     */
    public Set<String> getAgentsForTool(String toolName) {
        return toolAgentMap.getOrDefault(toolName, Collections.emptySet());
    }
    
    /**
     * 获取智能体支持的工具列表
     */
    public Set<String> getToolsForAgent(String agentName) {
        return agentToolMap.getOrDefault(agentName, Collections.emptySet());
    }
    
    /**
     * 判断工具是否支持指定智能体
     */
    public boolean isToolSupportedByAgent(String toolName, String agentName) {
        Set<String> agents = toolAgentMap.get(toolName);
        return agents != null && agents.contains(agentName);
    }

    
    /**
     * 获取注册表统计信息
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalTools", toolAgentMap.size());
        stats.put("totalAgents", agentToolMap.size());
        stats.put("toolAgentMap", toolAgentMap);
        stats.put("agentToolMap", agentToolMap);
        return stats;
    }
}
