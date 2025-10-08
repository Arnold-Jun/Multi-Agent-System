package com.xiaohongshu.codewiz.codewizagent.agentcore.agent.actions;

import com.xiaohongshu.codewiz.codewizagent.agentcore.agent.state.AgentMessageState;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;

/**
 * <p>
 *
 * </p>
 *
 * @author 瑞诺
 * create on 2025/4/10 16:25
 */
@Slf4j
public class ExecuteToolWaiting implements NodeAction<AgentMessageState> {
    @Override
    public Map<String, Object> apply(AgentMessageState state) throws Exception {
        log.info("ExecuteToolWaiting, state:{}", state);
        return Map.of();
    }
}
