package com.zhouruojun.travelingagent.mcp;

import com.zhouruojun.travelingagent.tools.TravelingToolCollection;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 旅游智能体工具提供者管理器
 */
@Slf4j
@Component
public class TravelingToolProviderManager {

    @Resource
    private MCPClientManager mcpClientManager;
    
    @Resource
    private TravelingToolCollection travelingToolCollection;
    
    @Resource
    private ToolRegistry toolRegistry;
    
    // 工具规格缓存
    private volatile List<ToolSpecification> cachedToolSpecifications;
    private volatile long lastCacheTime = 0;
    private static final long CACHE_DURATION_MS = 5 * 60 * 1000; // 5分钟缓存


    /**
     * 获取所有工具规格列表（带缓存）
     * 简化版本，不需要复杂的用户和会话管理
     * 
     * @return 工具规格列表
     */
    public List<ToolSpecification> getAllToolSpecifications() {
        try {
            // 检查缓存是否有效
            if (isCacheValid()) {
                return new ArrayList<>(cachedToolSpecifications);
            }
            
            List<ToolSpecification> allTools = new ArrayList<>();
            
            // 1. 获取本地工具规格
            List<ToolSpecification> localTools = travelingToolCollection.getToolSpecifications();
            allTools.addAll(localTools);
            
            // 2. 获取MCP工具规格
            List<ToolSpecification> mcpTools = mcpClientManager.getAllToolSpecifications();
            allTools.addAll(mcpTools);
            
            // 更新缓存
            updateCache(allTools);
            return allTools;
            
        } catch (Exception e) {
            log.error("获取工具规格失败", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * 检查缓存是否有效
     */
    private boolean isCacheValid() {
        return cachedToolSpecifications != null && 
               (System.currentTimeMillis() - lastCacheTime) < CACHE_DURATION_MS;
    }
    
    /**
     * 更新缓存
     */
    private void updateCache(List<ToolSpecification> toolSpecifications) {
        cachedToolSpecifications = new ArrayList<>(toolSpecifications);
        lastCacheTime = System.currentTimeMillis();
    }
    
    /**
     * 清除缓存（用于强制刷新）
     */
    public void clearCache() {
        cachedToolSpecifications = null;
        lastCacheTime = 0;
    }

    /**
     * 根据智能体名称获取工具规格
     * 实现智能工具分类，只返回相关工具
     * 
     * @param agentName 智能体名称
     * @return 过滤后的工具规格列表
     */
    public List<ToolSpecification> getToolSpecificationsByAgent(String agentName) {
        try {
            // 获取所有工具
            List<ToolSpecification> allTools = getAllToolSpecifications();
            
            // 根据智能体名称过滤工具
            List<ToolSpecification> filteredTools = filterToolsByAgent(allTools, agentName);
            
            return filteredTools;
            
        } catch (Exception e) {
            log.error("获取智能体工具规格失败: agentName={}", agentName, e);
            return new ArrayList<>();
        }
    }

    /**
     * 根据智能体名称过滤工具
     * 使用新的工具注册表进行分类
     */
    private List<ToolSpecification> filterToolsByAgent(List<ToolSpecification> allTools, String agentName) {
        return allTools.stream()
                .filter(tool -> isToolRelevantForAgent(tool, agentName))
                .collect(Collectors.toList());
    }

    /**
     * 判断工具是否与智能体相关
     * 使用工具注册表进行分类
     */
    private boolean isToolRelevantForAgent(ToolSpecification tool, String agentName) {
        String toolName = tool.name();
        
        // 使用工具注册表进行分类
        return toolRegistry.isToolSupportedByAgent(toolName, agentName);
    }


    /**
     * 执行工具调用
     * 统一处理MCP工具和本地工具的执行
     * 
     * @param requestId 请求ID
     * @param request 工具执行请求
     * @return 执行结果
     */
    public String executeTool(String requestId, ToolExecutionRequest request) {
        try {
            // 使用缓存的高效工具类型判断
            if (isMCPTool(request.name())) {
                return executeMCPTool(request);
            }
            
            // 执行本地工具
            return executeLocalTool(request);
            
        } catch (Exception e) {
            log.error("工具执行失败: requestId={}, tool={}", requestId, request.name(), e);
            // 重新抛出异常，让上层处理
            throw new RuntimeException("工具执行失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 判断工具是否为MCP工具（使用缓存优化）
     */
    private boolean isMCPTool(String toolName) {
        // 检查缓存是否有效
        if (isCacheValid()) {
            return cachedToolSpecifications.stream()
                    .anyMatch(spec -> spec.name().equals(toolName));
        }
        
        // 缓存无效时，使用轻量级检查
        return mcpClientManager.isMCPTool(toolName);
    }

    /**
     * 执行MCP工具
     */
    private String executeMCPTool(ToolExecutionRequest request) {
        try {
            // 尝试从traveling-mcp-server执行
            return mcpClientManager.executeTool("traveling-mcp-server", request);
        } catch (Exception e) {
            throw new RuntimeException("MCP工具执行失败: " + e.getMessage(), e);
        }
    }

    /**
     * 执行本地工具
     */
    private String executeLocalTool(ToolExecutionRequest request) {
        try {
            // 通过TravelingToolCollection执行本地工具
            return travelingToolCollection.executeTool(request, null);
        } catch (Exception e) {
            throw new RuntimeException("本地工具执行失败: " + e.getMessage(), e);
        }
    }


    /**
     * 检查MCP服务器健康状态
     */
    public boolean isMCPHealthy() {
        return mcpClientManager.getAllServerStatus().values().stream()
                .anyMatch(Boolean::booleanValue);
    }

    /**
     * 获取MCP服务器状态信息
     */
    public Map<String, Boolean> getMCPStatus() {
        return mcpClientManager.getAllServerStatus();
    }

}
