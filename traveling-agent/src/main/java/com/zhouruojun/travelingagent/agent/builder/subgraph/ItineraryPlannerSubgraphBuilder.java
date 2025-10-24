package com.zhouruojun.travelingagent.agent.builder.subgraph;

import com.zhouruojun.travelingagent.agent.actions.CallSubAgent;
import com.zhouruojun.travelingagent.agent.actions.ExecuteTools;
import com.zhouruojun.travelingagent.agent.builder.BaseSubgraphBuilder;
import com.zhouruojun.travelingagent.agent.serializers.AgentSerializers;
import com.zhouruojun.travelingagent.agent.state.SubgraphState;
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
 * ItineraryPlannerAgent 子图构建器
 * 负责将MetaSearchAgent的搜索结果组合成2-3个可行方案包
 */
@Slf4j
public class ItineraryPlannerSubgraphBuilder extends BaseSubgraphBuilder<SubgraphState> {

    @Override
    protected String getAgentName() {
        return "itineraryPlannerAgent";
    }

    @Override
    protected String getActionName() {
        return "itineraryPlanner";
    }

    @Override
    protected String getPrompt() {
        return promptManager.getSubgraphSystemPrompt("itineraryPlannerAgent");
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
        log.info("构建 ItineraryPlannerAgent 子图...");

        // 获取标准的路由逻辑
        EdgeAction<SubgraphState> agentShouldContinue = getStandardAgentShouldContinue();
        EdgeAction<SubgraphState> actionShouldContinue = getStandardActionShouldContinue();

        // 构建子图
        return new StateGraph<>(SubgraphState.SCHEMA, stateSerializer)
                .addNode("itineraryPlannerAgent", node_async(callAgent))
                .addNode("executeTools", node_async(executeTools))

                // 边连接
                .addEdge(START, "itineraryPlannerAgent")
                .addConditionalEdges("itineraryPlannerAgent",
                        edge_async(agentShouldContinue),
                        Map.of(
                                "action", "executeTools",
                                "END", END))
                .addConditionalEdges("executeTools",
                        edge_async(actionShouldContinue),
                        Map.of("callback", "itineraryPlannerAgent"));
    }

}
