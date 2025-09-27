package com.zhouruojun.dataanalysisagent.agent.builder.subgraph;

import com.zhouruojun.dataanalysisagent.agent.builder.AbstractAgentGraphBuilder;
import com.zhouruojun.dataanalysisagent.agent.actions.CallAgent;
import com.zhouruojun.dataanalysisagent.agent.actions.ExecuteTools;
import com.zhouruojun.dataanalysisagent.agent.serializers.AgentSerializers;
import com.zhouruojun.dataanalysisagent.agent.state.SubgraphState;
import com.zhouruojun.dataanalysisagent.common.PromptTemplateManager;
import com.zhouruojun.dataanalysisagent.tools.DataAnalysisToolCollection;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.EdgeAction;
import org.bsc.langgraph4j.serializer.StateSerializer;

import java.util.Map;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;

/**
 * 数据可视化子图构建器
 * 专门处理数据可视化相关的工具调用
 */
public class DataVisualizationSubgraphBuilder extends AbstractAgentGraphBuilder<SubgraphState> {

    @Override
    protected String getAgentName() {
        return "dataVisualizationAgent";
    }

    @Override
    protected String getActionName() {
        return "dataVisualizationAction";
    }

    @Override
    protected String getPrompt() {
        return PromptTemplateManager.instance.getDataVisualizationPrompt();
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
            stateSerializer = (StateSerializer<SubgraphState>) AgentSerializers.SUBGRAPH.subgraph();
        }
        super.initializeDefaults();
    }

    @Override
    protected StateGraph<SubgraphState> buildGraph(
            CallAgent<SubgraphState> callAgent,
            ExecuteTools<SubgraphState> executeTools
    ) throws GraphStateException {
        // 数据可视化子图：简化版本，直接输出结果
        // 直接让子智能体输出最终结果
        
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