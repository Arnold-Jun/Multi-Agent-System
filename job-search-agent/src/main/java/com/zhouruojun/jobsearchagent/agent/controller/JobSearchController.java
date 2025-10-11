package com.zhouruojun.jobsearchagent.agent.controller;

import com.alibaba.fastjson.JSONObject;
import com.zhouruojun.jobsearchagent.agent.dto.AgentChatRequest;
import com.zhouruojun.jobsearchagent.agent.core.JobSearchControllerCore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 求职智能体控制器
 * 提供求职相关的API接口
 */
@RestController
@RequestMapping("/api/job-search")
@Slf4j
public class JobSearchController {

    @Autowired
    private JobSearchControllerCore controllerCore;

    @PostMapping("/chat")
    public String chat(@RequestBody AgentChatRequest request) {
        log.info("Job search chat request: {}", JSONObject.toJSON(request));
        try {
            return controllerCore.processStreamReturnStr(request);
        } catch (Exception e) {
            log.error("Error processing job search request", e);
            return "错误: " + e.getMessage();
        }
    }

    @GetMapping("/capabilities")
    public String getCapabilities() {
        return """
        💼 求职智能体能力清单:
        
        **简历分析能力**:
        - 简历文件解析（PDF、Word、TXT格式）
        - 个人信息提取
        - 工作经历分析
        - 技能评估
        - 简历优化建议
        
        **岗位搜索能力**:
        - 多平台岗位搜索
        - 关键词匹配
        - 薪资范围筛选
        - 地理位置筛选
        - 岗位详情获取
        
        **匹配度分析**:
        - 简历与岗位匹配度计算
        - 技能匹配分析
        - 经验匹配评估
        - 综合匹配评分
        - 匹配度报告生成
        
        **职业建议**:
        - 职业发展路径建议
        - 技能提升建议
        - 求职策略指导
        - 面试准备建议
        - 职业规划咨询
        
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
                📊 求职智能体状态:
                
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
            testRequest.setChat("请帮我分析一下求职需求，搜索匹配的岗位");
            
            return controllerCore.processStreamReturnStr(testRequest);
        } catch (Exception e) {
            log.error("Error testing tool call", e);
            return "测试工具调用失败: " + e.getMessage();
        }
    }

}
