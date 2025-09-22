package com.zhouruojun.dataanalysisagent.a2a;

import com.zhouruojun.agentcore.spec.AgentCard;
import com.zhouruojun.dataanalysisagent.agent.core.DataAnalysisControllerCore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * A2A协议端点控制器
 * 提供标准的A2A协议端点
 */
@RestController
@Slf4j
public class A2AController {

    @Autowired
    private DataAnalysisAgentCard agentCard;

    @Autowired
    private DataAnalysisControllerCore dataAnalysisControllerCore;

    /**
     * A2A协议标准端点 - 智能体卡片信息
     */
    @GetMapping("/.well-known/agent.json")
    public ResponseEntity<AgentCard> getAgentCard() {
        log.info("Providing agent card via /.well-known/agent.json");
        return ResponseEntity.ok(agentCard.createAgentCard());
    }

    /**
     * A2A协议健康检查端点
     */
    @GetMapping("/a2a/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "healthy");
        response.put("timestamp", System.currentTimeMillis());
        response.put("service", "data-analysis-agent");
        return ResponseEntity.ok(response);
    }

    /**
     * A2A协议调用端点 - 处理智能体调用请求
     */
    @PostMapping("/a2a/invoke")
    public ResponseEntity<Map<String, Object>> invokeAgent(@RequestBody Map<String, Object> request) {
        try {
            String taskType = (String) request.get("taskType");
            @SuppressWarnings("unchecked")
            Map<String, Object> parameters = (Map<String, Object>) request.get("parameters");
            String sessionId = (String) request.get("sessionId");

            log.info("Received A2A invoke request: taskType={}, sessionId={}", taskType, sessionId);

            // 获取用户消息
            String userMessage = (String) parameters.getOrDefault("message", "请帮我分析数据");
            
            // 直接调用DataAnalysisControllerCore进行同步处理
            String result = dataAnalysisControllerCore.processA2aSync(sessionId, userMessage);

            Map<String, Object> response = new HashMap<>();
            response.put("status", "completed");
            response.put("result", result);
            response.put("taskType", taskType);
            response.put("sessionId", sessionId);

            log.info("A2A invoke completed successfully for taskType: {}", taskType);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error processing A2A invoke request", e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", "error");
            errorResponse.put("error", "Error processing request: " + e.getMessage());
            
            return ResponseEntity.ok(errorResponse);
        }
    }

    /**
     * A2A协议异步调用端点 - 处理异步智能体调用请求
     */
    @PostMapping("/a2a/invoke-async")
    public ResponseEntity<Map<String, Object>> invokeAgentAsync(@RequestBody Map<String, Object> request) {
        try {
            String taskType = (String) request.get("taskType");
            @SuppressWarnings("unchecked")
            Map<String, Object> parameters = (Map<String, Object>) request.get("parameters");
            String sessionId = (String) request.get("sessionId");

            log.info("Received A2A async invoke request: taskType={}, sessionId={}", taskType, sessionId);

            // 生成任务ID
            String taskId = "task_" + System.currentTimeMillis() + "_" + sessionId;

            // 异步处理任务
            CompletableFuture.runAsync(() -> {
                try {
                    String userMessage = (String) parameters.getOrDefault("message", "请帮我分析数据");
                    String result = dataAnalysisControllerCore.processA2aSync(sessionId, userMessage);
                    log.info("Async task {} completed with result: {}", taskId, result);
                } catch (Exception e) {
                    log.error("Async task {} failed", taskId, e);
                }
            });

            Map<String, Object> response = new HashMap<>();
            response.put("status", "accepted");
            response.put("taskId", taskId);
            response.put("message", "任务已提交，正在异步处理");

            return ResponseEntity.accepted().body(response);

        } catch (Exception e) {
            log.error("Error processing A2A async invoke request", e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", "error");
            errorResponse.put("error", "Error processing async request: " + e.getMessage());
            
            return ResponseEntity.ok(errorResponse);
        }
    }
}
