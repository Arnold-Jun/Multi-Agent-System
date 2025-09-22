package com.zhouruojun.agentcore.agent.actions;

import com.zhouruojun.agentcore.agent.state.AgentMessageState;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;

import java.util.Map;

/**
 * 用户输入节点
 * 处理需要用户交互的场景
 *
 */
@Slf4j
public class UserInput implements NodeAction<AgentMessageState> {

    @Override
    public Map<String, Object> apply(AgentMessageState state) throws Exception {
        log.info("UserInput executing - waiting for user interaction");
        
        // 用户输入节点主要是作为一个暂停点
        // 实际的用户输入会通过WebSocket或其他方式异步处理
        // 这里只是记录状态并等待继续
        
        String agentResponse = state.agentResponse().orElse("等待用户输入...");
        
        return Map.of(
            "next", "supervisor", // 用户输入后回到supervisor
            "agent_response", agentResponse,
            "user_input_required", true
        );
    }
}
