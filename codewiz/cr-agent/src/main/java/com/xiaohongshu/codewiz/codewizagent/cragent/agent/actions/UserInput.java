package com.xiaohongshu.codewiz.codewizagent.cragent.agent.actions;

import com.xiaohongshu.codewiz.codewizagent.cragent.agent.state.AgentMessageState;
import dev.langchain4j.data.message.AiMessage;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;

/**
 * <p>
 *
 * </p>
 *
 * @author 瑞诺
 * create on 2025/4/12 11:03
 */
@Slf4j
public class UserInput  implements NodeAction<AgentMessageState> {

//    private String requestId;
//
//    public UserInput(String requestId) {
//        this.requestId = requestId;
//    }

    @Override
    public Map<String, Object> apply(AgentMessageState state) throws Exception {
        log.info("UserInput, state:{}", state.lastMessage());
        return Map.of("next","", "tool_interceptor", "",
                "tool_post_interceptor", "", "agent_response", "",
                "messages", state.lastMessage().orElse(AiMessage.aiMessage("等待用户输入")));
    }
}
