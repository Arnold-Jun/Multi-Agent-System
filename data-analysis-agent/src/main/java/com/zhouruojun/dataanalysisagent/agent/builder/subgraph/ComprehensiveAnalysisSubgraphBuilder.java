package com.zhouruojun.dataanalysisagent.agent.builder.subgraph;

import com.zhouruojun.dataanalysisagent.agent.builder.AbstractAgentGraphBuilder;
import com.zhouruojun.dataanalysisagent.agent.actions.CallAgent;
import com.zhouruojun.dataanalysisagent.agent.actions.ExecuteTools;
import com.zhouruojun.dataanalysisagent.agent.serializers.AgentSerializers;
import com.zhouruojun.dataanalysisagent.agent.state.SubgraphState;
import com.zhouruojun.dataanalysisagent.common.PromptTemplateManager;
import com.zhouruojun.dataanalysisagent.tools.DataAnalysisToolCollection;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.EdgeAction;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.serializer.StateSerializer;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * 综合分析子图构建器
 * 负责构建综合分析智能体的执行图
 */
@Slf4j
public class ComprehensiveAnalysisSubgraphBuilder extends AbstractAgentGraphBuilder<SubgraphState> {

    /**
     * 获取智能体名称
     */
    @Override
    protected String getAgentName() {
        return "comprehensiveAnalysisAgent";
    }

    /**
     * 获取动作名称
     */
    @Override
    protected String getActionName() {
        return "comprehensiveAnalysisAction";
    }

    /**
     * 获取提示词
     */
    @Override
    protected String getPrompt() {
        return PromptTemplateManager.instance.getPrompt("comprehensiveAnalysis");
    }

    /**
     * 创建临时工具集合
     */
    @Override
    protected DataAnalysisToolCollection createTempToolCollection() {
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

    /**
     * 构建综合分析子图
     */
    @Override
    protected StateGraph<SubgraphState> buildGraph(
            CallAgent<SubgraphState> callAgent,
            ExecuteTools<SubgraphState> executeTools
    ) throws GraphStateException {
        // 综合分析子图：直接输出结果

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
                        java.util.Map.of("action", getActionName(), "FINISH", END))
                .addConditionalEdges(getActionName(),
                        edge_async(actionShouldContinue),
                        java.util.Map.of("callback", END));
    }
}
