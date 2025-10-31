package com.zhouruojun.travelingagent.agent.actions;

import com.zhouruojun.travelingagent.agent.state.MainGraphState;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;

import java.util.HashMap;
import java.util.Map;

/**
 * 文本输入节点
 * 专门处理文本输入场景
 * 
 * 功能说明：
 * 1. 从scheduler路由而来，用于收集用户的文本回复
 * 2. 作为图执行的暂停点，等待用户输入文本
 * 3. 使用subgraphTaskDescription作为询问消息
 * 4. 用户输入后，通过humanInput恢复执行，路由回scheduler继续流程
 */
@Slf4j
public class TextInput implements NodeAction<MainGraphState> {

    @Override
    public Map<String, Object> apply(MainGraphState state) throws Exception {
        log.info("TextInput executing - waiting for text input for session: {}", state.getSessionId().orElse("unknown"));
        
        Map<String, Object> result = new HashMap<>();
        
        // 获取询问消息
        String inquiryMessage;
        
        // Scheduler路由：使用taskDescription作为询问消息
        if (state.getValue("subgraphTaskDescription").isPresent()) {
            inquiryMessage = (String) state.getValue("subgraphTaskDescription").get();
        }
        // 兜底情况：使用默认消息
        else {
            inquiryMessage = "需要您提供更多的信息以继续...";
            log.warn("TextInput triggered without specific message, using default");
        }

        // 返回状态，标记需要用户输入（文本输入）
        // 注意：由于使用了 interruptAfter，图会在此节点后暂停
        result.put("finalResponse", inquiryMessage);
        result.put("userInputRequired", true);
        // 确保不设置formInputRequired（文本输入场景）
        result.put("formInputRequired", false);
        
        log.info("TextInput: 已设置文本输入标志，等待用户输入文本");
        
        return result;
    }
}

