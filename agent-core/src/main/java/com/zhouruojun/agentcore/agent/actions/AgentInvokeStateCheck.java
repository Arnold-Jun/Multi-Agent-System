package com.zhouruojun.agentcore.agent.actions;

import com.zhouruojun.agentcore.agent.state.AgentMessageState;
import com.zhouruojun.agentcore.a2a.A2aClientManager;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;

import java.util.Map;
import java.util.Optional;

/**
 * Agent调用状态检查节点
 * 检查智能体调用的状态并决定下一步操作
 * 
 */
@Slf4j
public class AgentInvokeStateCheck implements NodeAction<AgentMessageState> {

    private final A2aClientManager a2aClientManager;

    public AgentInvokeStateCheck(A2aClientManager a2aClientManager) {
        this.a2aClientManager = a2aClientManager;
    }

    @Override
    public Map<String, Object> apply(AgentMessageState state) throws Exception {
        log.info("=== AgentInvokeStateCheck executing ===");
        log.info("AgentInvokeStateCheck state: {}", state.data());
        
        Optional<String> currentAgent = state.currentAgent();
        Optional<String> agentResponse = state.agentResponse();
        
        log.info("Current agent: {}, Agent response present: {}", currentAgent, agentResponse.isPresent());
        
        if (currentAgent.isEmpty()) {
            log.warn("No current agent specified, finishing");
            return Map.of("next", "FINISH");
        }
        
        String agentName = currentAgent.get();
        String response = agentResponse.orElse("");
        
        // 检查智能体健康状态
        boolean isHealthy = a2aClientManager != null ? 
            a2aClientManager.checkAgentHealth(agentName) : true;
        if (!isHealthy) {
            log.warn("Agent {} is not healthy, finishing", agentName);
            return Map.of(
                "next", "FINISH",
                "agent_response", "智能体 " + agentName + " 状态异常"
            );
        }
        
        // 检查响应内容
        if (response.isEmpty()) {
            log.warn("Empty response from agent {}, retrying", agentName);
            return Map.of("next", "continue");
        }
        
        // 检查是否包含错误信息
        if (response.toLowerCase().contains("error") || response.toLowerCase().contains("错误")) {
            log.warn("Agent {} returned error response: {}", agentName, response);
            return Map.of(
                "next", "FINISH",
                "agent_response", response
            );
        }
        
        // 成功处理，返回到supervisor进行最终推理
        log.info("Agent {} completed successfully, returning to supervisor for final reasoning", agentName);
        return Map.of(
            "next", "supervisor",
            "agent_response", response,
            "processingStage", "final_reasoning"  // 标记为最终推理阶段
        );
    }
}
