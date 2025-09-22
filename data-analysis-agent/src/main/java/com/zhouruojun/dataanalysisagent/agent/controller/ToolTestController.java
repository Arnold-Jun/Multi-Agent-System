package com.zhouruojun.dataanalysisagent.agent.controller;

import com.zhouruojun.dataanalysisagent.tools.DataAnalysisToolCollection;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 工具测试控制器
 * 用于测试和验证工具扫描机制
 */
@RestController
@RequestMapping("/api/tools")
@Slf4j
public class ToolTestController {
    
    @Autowired
    private DataAnalysisToolCollection toolCollection;

    
    /**
     * 重新扫描工具
     */
    @PostMapping("/rescan")
    public Map<String, Object> rescanTools() {
        log.info("Rescanning tools");
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            toolCollection.rescanTools();
            result.put("success", true);
            result.put("message", "Tools rescanned successfully");
            result.put("toolCount", toolCollection.getToolCount());
            result.put("toolNames", toolCollection.getToolNames());
            
        } catch (Exception e) {
            log.error("Error rescanning tools", e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        
        return result;
    }
    
    /**
     * 获取工具信息
     */
    @GetMapping("/info")
    public Map<String, Object> getToolInfo() {
        Map<String, Object> result = new HashMap<>();
        
        result.put("toolCount", toolCollection.getToolCount());
        result.put("toolNames", toolCollection.getToolNames());
        result.put("toolSpecifications", toolCollection.getToolSpecifications());
        result.put("cachedToolMethods", toolCollection.getCachedToolMethods());
        result.put("cacheStats", toolCollection.getCacheStats());
        
        return result;
    }
}
