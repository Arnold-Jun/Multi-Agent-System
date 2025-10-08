package com.zhouruojun.agentcore.agent.controller;

import com.alibaba.fastjson.JSONObject;
import com.zhouruojun.a2acore.core.PushNotificationAuth;
import com.zhouruojun.a2acore.spec.AgentCard;
import com.zhouruojun.a2acore.spec.Task;
import com.zhouruojun.a2acore.spec.TaskState;
import com.zhouruojun.a2acore.spec.TaskStatusUpdateEvent;
import com.zhouruojun.agentcore.a2a.A2aClientManager;
import com.zhouruojun.agentcore.a2a.A2aRegister;
import com.zhouruojun.agentcore.agent.core.AgentControllerCore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * A2A Agent控制器
 * 提供A2A协议的Agent注册和管理功能
 *
 */
@RestController
@RequestMapping("/a2a")
@Validated
@ResponseBody
@Slf4j
public class AgentA2AController {

    @Autowired
    private A2aClientManager a2aClientManager;
    
    @Autowired
    private AgentControllerCore agentControllerCore;

    /**
     * 注册A2A Agent
     */
    @PostMapping("/register")
    public AgentCard registerA2AAgent(@RequestBody A2aRegister register) {
        log.info("A2A Agent注册: {}", JSONObject.toJSONString(register));
        return a2aClientManager.register(register);
    }
    
    /**
     * 接收Push Notification（POST）
     * 处理来自其他Agent（如job-search-agent）的任务完成通知
     */
    @PostMapping("/notifications")
    public ResponseEntity<String> handleNotification(
            @RequestHeader(value = PushNotificationAuth.AUTH_HEADER, required = false) String authorization,
            @RequestParam(value = "validationToken", required = false) String validationToken,
            @RequestBody(required = false) Task task) {
        
        // 处理验证请求（用于验证endpoint是否可达）
        if (validationToken != null) {
            log.info("Received validation request with token: {}", validationToken);
            return ResponseEntity.ok(validationToken);
        }
        
        // 处理实际的notification
        if (task == null) {
            log.error("Received notification with null task");
            return ResponseEntity.badRequest().body("Task is null");
        }
        
        log.info("✅ Received push notification for task: {}, state: {}", 
            task.getId(), 
            task.getStatus() != null ? task.getStatus().getState() : "null");
        
        try {
            // 验证任务数据完整性
            if (task.getStatus() == null) {
                log.error("Push notification received, but status is null, data: {}", 
                    JSONObject.toJSONString(task));
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Push notification received, but status is null");
            }
            
            // 对于某些中间状态，message可能为null，这是正常的
            // 但对于最终状态（COMPLETED/FAILED），必须有message
            TaskState state = task.getStatus().getState();
            if ((state == TaskState.COMPLETED || state == TaskState.FAILED) && 
                task.getStatus().getMessage() == null) {
                log.error("Push notification received with final state but no message, data: {}", 
                    JSONObject.toJSONString(task));
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Push notification received with final state but no message");
            }
            
            // 将notification转换为A2A update event并传递给AgentControllerCore
            TaskStatusUpdateEvent updateEvent = new TaskStatusUpdateEvent();
            updateEvent.setId(task.getId());
            updateEvent.setStatus(task.getStatus());
            updateEvent.setFinalFlag(task.getStatus().getState() == TaskState.COMPLETED || 
                                      task.getStatus().getState() == TaskState.FAILED);
            
            log.info("📨 Forwarding notification to AgentControllerCore: taskId={}, sessionId={}, state={}", 
                task.getId(), task.getSessionId(), task.getStatus().getState().getState());
            
            // 调用AgentControllerCore处理A2A消息
            agentControllerCore.a2aAiMessageOnEvent(updateEvent);
            
            // 如果任务已完成，记录日志
            if (task.getStatus().getState() == TaskState.COMPLETED) {
                log.info("🎉 Task {} completed successfully", task.getId());
            } else if (task.getStatus().getState() == TaskState.FAILED) {
                log.error("❌ Task {} failed", task.getId());
            }
            
            return ResponseEntity.ok("Notification processed successfully");
            
        } catch (Exception e) {
            log.error("❌ Error processing push notification for task {}: {}", 
                task.getId(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Error processing notification: " + e.getMessage());
        }
    }
    
    /**
     * 验证Push Notification Endpoint（GET）
     * 用于A2A协议验证endpoint是否可达
     */
    @GetMapping("/notifications")
    public ResponseEntity<String> validateNotificationEndpoint(
            @RequestParam(value = "validationToken", required = false) String validationToken) {
        
        if (validationToken != null) {
            log.info("Endpoint validation requested with token: {}", validationToken);
            return ResponseEntity.ok(validationToken);
        }
        
        log.info("Notification endpoint health check");
        return ResponseEntity.ok("A2A notification endpoint is active");
    }

    /**
     * 获取A2A配置
     */
    @GetMapping("/a2aConfig")
    public AgentCard getA2AConfig(@RequestParam() String agentName) {
        return a2aClientManager.getA2aClient(agentName).getAgentCard();
    }

    /**
     * 列出所有A2A Agent
     */
    @GetMapping("/list")
    public List<AgentCard> listA2AAgents() {
        return a2aClientManager.getAgentCards();
    }
}
