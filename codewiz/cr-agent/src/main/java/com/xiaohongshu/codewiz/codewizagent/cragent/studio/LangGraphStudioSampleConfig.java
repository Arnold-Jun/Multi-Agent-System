package com.xiaohongshu.codewiz.codewizagent.cragent.studio;

import com.xiaohongshu.codewiz.codewizagent.cragent.agent.core.AgentControllerCore;
import com.xiaohongshu.codewiz.codewizagent.cragent.agent.state.AgentMessageState;
import javax.annotation.Resource;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.studio.springboot.AbstractLangGraphStudioConfig;
import org.bsc.langgraph4j.studio.springboot.LangGraphFlow;
import org.springframework.context.annotation.Configuration;

/**
 * 这东西是langGraph提供的调Workflow的工具类，有页面。 启动后可以看到。但是这个东西只能够用于简单的链路串联，不能用于Chat的方式
 */
@Configuration
public class LangGraphStudioSampleConfig extends AbstractLangGraphStudioConfig {

    private LangGraphFlow flow = null;

    @Resource
    private AgentControllerCore agentControllerCore;

//    public LangGraphStudioSampleConfig() throws GraphStateException {

//        var workflow = new StateGraph<>(AgentMessageState::new);
//
//        // define your workflow
//        this.flow = LangGraphFlow.builder()
//                .title("LangGraph Studio")
//                .stateGraph(workflow)
//                .build();

//    }

    @Override
    public LangGraphFlow getFlow() {
        if (flow == null) {
            StateGraph<AgentMessageState> workflow = null;
            try {
                workflow = agentControllerCore.buildGraph(null, "liuyjanghong", "123456");
            } catch (GraphStateException e) {
                throw new RuntimeException(e);
            }
            this.flow = LangGraphFlow.builder()
                    .title("LangGraph Studio")
                    .stateGraph(workflow)
                    .addInputStringArg("messages")
                    .build();
        }
        return this.flow;
    }
}
