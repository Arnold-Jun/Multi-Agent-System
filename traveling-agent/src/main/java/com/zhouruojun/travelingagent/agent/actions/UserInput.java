package com.zhouruojun.travelingagent.agent.actions;

import com.zhouruojun.travelingagent.agent.state.MainGraphState;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;

import java.util.Map;

/**
 * 用户输入节点
 * 处理需要用户交互的场景
 * 
 * 功能说明：
 * 1. 作为图执行的暂停点，等待用户输入
 * 2. 保留finalResponse作为询问消息
 * 3. 设置user_input_required标志
 * 4. 用户输入后通过humanInput恢复执行，回到scheduler继续
 */
@Slf4j
public class UserInput implements NodeAction<MainGraphState> {

    @Override
    public Map<String, Object> apply(MainGraphState state) throws Exception {
        log.info("UserInput executing - waiting for user interaction for session: {}", state.getSessionId().orElse("unknown"));
        
        String inquiryMessage;
        
        // Scheduler路由：使用taskDescription作为询问消息
        // 这是主要的用户输入场景，Scheduler判断需要用户输入时会设置taskDescription
        if (state.getValue("subgraphTaskDescription").isPresent()) {
            inquiryMessage = (String) state.getValue("subgraphTaskDescription").get();
        }
        // 兜底情况：使用默认消息
        else {
            inquiryMessage = "需要您提供更多的信息以继续...";
            log.warn("UserInput triggered without specific message, using default");
        }

        // 返回状态，标记需要用户输入
        // 注意：由于使用了 interruptAfter("userInput")，图会在此节点后暂停
        // 用户输入后，图会从当前状态继续执行
        return Map.of(
            "finalResponse", inquiryMessage,
            "userInputRequired", true
        );
    }
}

