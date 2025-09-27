package com.zhouruojun.dataanalysisagent.agent.controller;

import com.alibaba.fastjson.JSONObject;
import com.zhouruojun.dataanalysisagent.agent.AgentChatRequest;
import com.zhouruojun.dataanalysisagent.agent.core.DataAnalysisControllerCore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 数据分析智能体控制器
 * 提供数据分析相关的API接口
 */
@RestController
@RequestMapping("/api/data-analysis")
@Slf4j
public class DataAnalysisController {

    @Autowired
    private DataAnalysisControllerCore controllerCore;

    @PostMapping("/chat")
    public String chat(@RequestBody AgentChatRequest request) {
        log.info("Data analysis chat request: {}", JSONObject.toJSON(request));
        try {
            return controllerCore.processStreamReturnStr(request);
        } catch (Exception e) {
            log.error("Error processing data analysis request", e);
            return "错误: " + e.getMessage();
        }
    }

    @GetMapping("/capabilities")
    public String getCapabilities() {
        return """
        🔬 数据分析智能体能力清单:
        
        **数据处理能力**:
        - 数据清洗和预处理
        - 数据质量评估
        - 缺失值处理
        - 异常值检测
        
        **统计分析能力**:
        - 描述性统计分析
        - 相关性分析
        - 异常值检测
        - 分布分析
        
        **数据可视化**:
        - 柱状图生成
        - 饼图生成
        - 散点图生成
        - 折线图生成
        - 直方图生成
        
        **网络搜索**:
        - 实时信息搜索
        - 专业资料查找
        - 新闻和趋势搜索
        
        **工具调用方式**:
        - 通过ExecuteTool节点调用
        - 支持ToolSpecification注入
        - 基于LangGraph4j工作流
        """;
    }

    @GetMapping("/status")
    public String getStatus() {
        try {
            String ollamaStatus = controllerCore.checkOllamaStatus();
            String graphStatus = JSONObject.toJSONString(controllerCore.getGraphStatus());
            
            return String.format("""
                📊 数据分析智能体状态:
                
                **Ollama服务**: %s
                
                **图状态**: %s
                
                **活跃会话**: %d
                """, 
                ollamaStatus, 
                graphStatus,
                controllerCore.getActiveSessionCount()
            );
        } catch (Exception e) {
            log.error("Error getting status", e);
            return "获取状态失败: " + e.getMessage();
        }
    }

    @GetMapping("/models")
    public String getModels() {
        try {
            return controllerCore.getAvailableModels();
        } catch (Exception e) {
            log.error("Error getting models", e);
            return "获取模型列表失败: " + e.getMessage();
        }
    }

    @GetMapping("/sessions")
    public String getSessions() {
        try {
            return JSONObject.toJSONString(controllerCore.getActiveSessions());
        } catch (Exception e) {
            log.error("Error getting sessions", e);
            return "获取会话列表失败: " + e.getMessage();
        }
    }

    @DeleteMapping("/sessions/{sessionId}")
    public String clearSession(@PathVariable String sessionId) {
        try {
            controllerCore.clearSessionHistory(sessionId);
            return "会话 " + sessionId + " 已清除";
        } catch (Exception e) {
            log.error("Error clearing session", e);
            return "清除会话失败: " + e.getMessage();
        }
    }

    @PostMapping("/test")
    public String testToolCall() {
        try {
            AgentChatRequest testRequest = new AgentChatRequest();
            testRequest.setSessionId("test-session-" + System.currentTimeMillis());
            testRequest.setChat("请帮我分析一下数据，生成一个简单的柱状图");
            
            return controllerCore.processStreamReturnStr(testRequest);
        } catch (Exception e) {
            log.error("Error testing tool call", e);
            return "测试工具调用失败: " + e.getMessage();
        }
    }
}