package com.zhouruojun.dataanalysisagent.agent.builder.subgraph;

import com.zhouruojun.dataanalysisagent.agent.builder.AbstractAgentGraphBuilder;
import com.zhouruojun.dataanalysisagent.agent.actions.CallSubAgent;
import com.zhouruojun.dataanalysisagent.agent.actions.ExecuteTools;
import com.zhouruojun.dataanalysisagent.agent.serializers.AgentSerializers;
import com.zhouruojun.dataanalysisagent.agent.state.SubgraphState;
import com.zhouruojun.dataanalysisagent.common.PromptTemplateManager;
import com.zhouruojun.dataanalysisagent.tools.DataAnalysisToolCollection;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.EdgeAction;

import java.util.Map;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;

/**
 * 网络搜索子图构建器
 * 专门处理网络搜索相关的工具调用
 */
public class WebSearchSubgraphBuilder extends AbstractAgentGraphBuilder<SubgraphState> {

    @Override
    protected String getAgentName() {
        return "webSearchAgent";
    }

    @Override
    protected String getActionName() {
        return "webSearchAction";
    }

    @Override
    protected String getPrompt() {
        return PromptTemplateManager.instance.getWebSearchPrompt();
    }

    @Override
    protected DataAnalysisToolCollection createTempToolCollection() {
        // 使用工厂方法创建子图工具集合，工厂方法会自动获取对应工具
        return DataAnalysisToolCollection.createSubgraphToolCollection(getAgentName(), mainToolCollection);
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
    protected StateGraph<SubgraphState> buildGraph(
            CallSubAgent callAgent,
            ExecuteTools<SubgraphState> executeTools
    ) throws GraphStateException {
        // 使用基类提供的标准边条件
        EdgeAction<SubgraphState> agentShouldContinue = getStandardAgentShouldContinue();
        EdgeAction<SubgraphState> actionShouldContinue = getStandardActionShouldContinue();
        
        // 构建简化的状态图 - 直接输出结果
        return new StateGraph<>(org.bsc.langgraph4j.prebuilt.MessagesState.SCHEMA, stateSerializer)
                .addNode(getAgentName(), node_async(callAgent))
                .addNode(getActionName(), node_async(executeTools))
                .addEdge(START, getAgentName())
                .addConditionalEdges(getAgentName(),
                        edge_async(agentShouldContinue),
                        Map.of(
                                "action", getActionName(),
                                "FINISH", END))
                .addConditionalEdges(getActionName(),
                        edge_async(actionShouldContinue),
                        Map.of("callback", END));
    }
}