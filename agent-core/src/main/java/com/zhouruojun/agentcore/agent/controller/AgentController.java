package com.zhouruojun.agentcore.agent.controller;

import com.alibaba.fastjson.JSONObject;
import com.zhouruojun.agentcore.agent.AgentChatRequest;
import com.zhouruojun.agentcore.agent.ChatRequest;
import com.zhouruojun.agentcore.agent.ChatResponse;
import com.zhouruojun.agentcore.agent.core.AgentControllerCore;
import com.zhouruojun.agentcore.service.OllamaTestService;
import com.zhouruojun.agentcore.a2a.A2aSseEventHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.HashMap;
import java.util.Map;

/**
 * Agent控制器
 */
@RestController
@RequestMapping("/api/agent")
@Slf4j
public class AgentController {

    private final AgentControllerCore agentControllerCore;
    private final OllamaTestService ollamaTestService;
    
    @Autowired(required = false)
    private A2aSseEventHandler a2aSseEventHandler;

    public AgentController(AgentControllerCore agentControllerCore, OllamaTestService ollamaTestService) {
        this.agentControllerCore = agentControllerCore;
        this.ollamaTestService = ollamaTestService;
    }

    @PostMapping("/chat")
    public ChatResponse chat(@RequestBody ChatRequest request) {
        log.info("Chat request: {}", JSONObject.toJSON(request));
        try {
            // 转换为内部请求格式
            AgentChatRequest agentRequest = new AgentChatRequest(request.getMessage(), request.getSessionId());
            String result = agentControllerCore.processStreamReturnStr(agentRequest);
            return ChatResponse.success(result, request.getSessionId());
        } catch (Exception e) {
            log.error("Chat error", e);
            return ChatResponse.error("处理请求时发生错误: " + e.getMessage(), request.getSessionId());
        }
    }

    @GetMapping("/testState")
    public String testState(@RequestParam(value = "requestId") String requestId) {
        try {
            agentControllerCore.viewState(requestId);
            return "OK";
        } catch (Exception e) {
            log.error("Test state error", e);
            return "Error: " + e.getMessage();
        }
    }

    @GetMapping("/health")
    public String health() {
        return "Agent Core is running!";
    }
    
    @GetMapping("/ollama/status")
    public String checkOllamaStatus() {
        try {
            return agentControllerCore.checkOllamaStatus();
        } catch (Exception e) {
            log.error("Check Ollama status error", e);
            return "Error: " + e.getMessage();
        }
    }
    
    @GetMapping("/ollama/models")
    public String getAvailableModels() {
        try {
            return agentControllerCore.getAvailableModels();
        } catch (Exception e) {
            log.error("Get models error", e);
            return "Error: " + e.getMessage();
        }
    }
    
    @GetMapping("/ollama/diagnose")
    public String diagnoseOllama() {
        try {
            return ollamaTestService.diagnose();
        } catch (Exception e) {
            log.error("Diagnose Ollama error", e);
            return "诊断失败: " + e.getMessage();
        }
    }
    
    @GetMapping("/sessions")
    public Map<String, Object> getActiveSessions() {
        try {
            Map<String, Integer> sessions = agentControllerCore.getActiveSessions();
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("sessions", sessions);
            response.put("totalSessions", sessions.size());
            response.put("timestamp", System.currentTimeMillis());
            return response;
        } catch (Exception e) {
            log.error("Get sessions error", e);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("error", e.getMessage());
            return response;
        }
    }
    
    @GetMapping("/agents/status")
    public Map<String, Object> getAgentsStatus() {
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("timestamp", System.currentTimeMillis());
            
            // 检查数据分析智能体状态
            boolean dataAnalysisAgentHealthy = agentControllerCore.checkDataAnalysisAgentHealth();
            response.put("data-analysis-agent", Map.of(
                "healthy", dataAnalysisAgentHealthy,
                "url", "http://localhost:8082",
                "status", dataAnalysisAgentHealthy ? "运行中" : "不可用"
            ));
            
            return response;
        } catch (Exception e) {
            log.error("Get agents status error", e);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("error", e.getMessage());
            return response;
        }
    }
    
    @PostMapping("/agents/reregister")
    public Map<String, Object> reregisterAgents() {
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("timestamp", System.currentTimeMillis());
            
            // 重新注册数据分析智能体
            agentControllerCore.reregisterDataAnalysisAgent();
            
            // 检查重新注册后的状态
            boolean dataAnalysisAgentHealthy = agentControllerCore.checkDataAnalysisAgentHealth();
            response.put("data-analysis-agent", Map.of(
                "healthy", dataAnalysisAgentHealthy,
                "url", "http://localhost:8082",
                "status", dataAnalysisAgentHealthy ? "运行中" : "不可用",
                "reregistered", true
            ));
            
            return response;
        } catch (Exception e) {
            log.error("Reregister agents error", e);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("error", e.getMessage());
            return response;
        }
    }
    
    @DeleteMapping("/sessions/{sessionId}")
    public Map<String, Object> clearSession(@PathVariable String sessionId) {
        try {
            agentControllerCore.clearSessionHistory(sessionId);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "会话已清除");
            response.put("sessionId", sessionId);
            return response;
        } catch (Exception e) {
            log.error("Clear session error", e);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("error", e.getMessage());
            return response;
        }
    }
    
    @GetMapping("/async-result/{sessionId}")
    public Map<String, Object> getAsyncResult(@PathVariable String sessionId) {
        try {
            boolean completed = agentControllerCore.isAsyncCompleted(sessionId);
            Map<String, Object> response = new HashMap<>();
            response.put("completed", completed);
            
            if (completed) {
                String result = agentControllerCore.getAsyncResult(sessionId);
                response.put("result", result);
                response.put("status", "success");
            } else {
                response.put("status", "pending");
                response.put("message", "任务仍在处理中，请稍候...");
            }
            
            return response;
        } catch (Exception e) {
            log.error("Get async result error", e);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("error", e.getMessage());
            return response;
        }
    }
    
    /**
     * 建立SSE连接用于实时推送异步结果
     */
    @GetMapping(value = "/sse/{sessionId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter connectSSE(@PathVariable String sessionId) {
        log.info("Establishing SSE connection for session: {}", sessionId);
        
        SseEmitter emitter = new SseEmitter(1_800_000L); // 30分钟超时，给足够时间处理任务
        
        if (a2aSseEventHandler != null) {
            a2aSseEventHandler.registerFrontendConnection(sessionId, emitter);
        }
        
        return emitter;
    }
}


