package com.zhouruojun.travelingagent.agent.builder.subgraph;

import com.zhouruojun.travelingagent.agent.actions.CallSubAgent;
import com.zhouruojun.travelingagent.agent.actions.ExecuteTools;
import com.zhouruojun.travelingagent.agent.builder.BaseSubgraphBuilder;
import com.zhouruojun.travelingagent.agent.serializers.AgentSerializers;
import com.zhouruojun.travelingagent.agent.state.SubgraphState;
import com.zhouruojun.travelingagent.common.PromptTemplateManager;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.EdgeAction;

import java.util.Map;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * MetaSearchAgent 子图构建器
 * 负责解析用户意图，进行信息搜索
 */
@Slf4j
public class MetaSearchSubgraphBuilder extends BaseSubgraphBuilder<SubgraphState> {

    @Override
    protected String getAgentName() {
        return "metaSearchAgent";
    }

    @Override
    protected String getActionName() {
        return "metaSearch";
    }

    @Override
    protected String getPrompt() {
        return PromptTemplateManager.instance.getMetaSearchPrompt();
    }

    @Override
    protected void initializeDefaults() {
        // 设置子图序列化器
        if (stateSerializer == null) {
            stateSerializer = AgentSerializers.SUBGRAPH.subgraph();
        }
        super.initializeDefaults();
    }

    @Override
    protected StateGraph<SubgraphState> buildGraph(CallSubAgent callAgent, ExecuteTools<SubgraphState> executeTools) throws GraphStateException {
        log.info("构建 MetaSearchAgent 子图...");

        // 获取标准的路由逻辑
        EdgeAction<SubgraphState> agentShouldContinue = getStandardAgentShouldContinue();
        EdgeAction<SubgraphState> actionShouldContinue = getStandardActionShouldContinue();

        // 构建子图
        return new StateGraph<>(SubgraphState.SCHEMA, stateSerializer)
                .addNode("metaSearchAgent", node_async(callAgent))
                .addNode("executeTools", node_async(executeTools))

                // 边连接
                .addEdge(START, "metaSearchAgent")
                .addConditionalEdges("metaSearchAgent",
                        edge_async(agentShouldContinue),
                        Map.of(
                                "action", "executeTools",
                                "END", END))
                .addConditionalEdges("executeTools",
                        edge_async(actionShouldContinue),
                        Map.of("callback", "metaSearchAgent"));
    }

}
