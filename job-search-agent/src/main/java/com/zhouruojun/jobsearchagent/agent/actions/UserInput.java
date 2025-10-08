package com.zhouruojun.jobsearchagent.agent.actions;

import com.zhouruojun.jobsearchagent.agent.state.MainGraphState;
import dev.langchain4j.data.message.AiMessage;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;

/**
 * UserInput节点
 * 用于A2A交互中的用户输入场景
 * 完全对齐codewiz/cr-agent的UserInput实现
 * 
 * 当Scheduler判断需要用户提供额外信息时，会路由到此节点
 * 此节点会将Scheduler输出的context（执行上下文）作为用户输入提示返回给调用方
 */
@Slf4j
public class UserInput implements NodeAction<MainGraphState> {

    /**
     * 处理用户输入节点
     * 读取Scheduler设置的subgraphContext作为用户输入提示
     * 
     * @param state 当前图状态
     * @return 返回包含用户输入提示的消息
     */
    @Override
    public Map<String, Object> apply(MainGraphState state) throws Exception {
        log.info("JobSearchAgent UserInput node triggered");
        
        // 优先使用Scheduler设置的context（详细的执行指令）
        // 如果没有context，则使用taskDescription
        // 如果都没有，则使用默认提示
        String userInputPrompt = state.getSubgraphContext()
                .or(() -> state.getSubgraphTaskDescription())
                .orElse("等待用户输入");
        
        log.info("UserInput prompt: {}", userInputPrompt);
        
        // 返回AiMessage，包含需要用户输入的提示信息
        return Map.of(
                "next", "", 
                "agent_response", "",
                "messages", AiMessage.aiMessage(userInputPrompt)
        );
    }
}

