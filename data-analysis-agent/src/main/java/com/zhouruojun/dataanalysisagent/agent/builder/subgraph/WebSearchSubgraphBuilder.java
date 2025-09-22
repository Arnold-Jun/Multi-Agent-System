package com.zhouruojun.dataanalysisagent.agent.builder.subgraph;

import com.zhouruojun.dataanalysisagent.agent.builder.AbstractAgentGraphBuilder;
import com.zhouruojun.dataanalysisagent.agent.actions.CallAgent;
import com.zhouruojun.dataanalysisagent.agent.actions.ExecuteTools;
import com.zhouruojun.dataanalysisagent.agent.state.AgentMessageState;
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
public class WebSearchSubgraphBuilder extends AbstractAgentGraphBuilder {

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
    protected StateGraph<AgentMessageState> buildGraph(
            CallAgent callAgent,
            ExecuteTools executeTools
    ) throws GraphStateException {
        // 使用基类提供的标准边条件
        EdgeAction<AgentMessageState> agentShouldContinue = getStandardAgentShouldContinue();
        EdgeAction<AgentMessageState> actionShouldContinue = getStandardActionShouldContinue();
        
        // 构建简化的状态图 - 直接输出结果
        return new StateGraph<>(AgentMessageState.SCHEMA, stateSerializer)
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
                        Map.of("callback", getAgentName()));
    }
}