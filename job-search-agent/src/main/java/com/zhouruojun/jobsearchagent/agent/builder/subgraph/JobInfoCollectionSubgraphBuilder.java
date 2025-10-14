package com.zhouruojun.jobsearchagent.agent.builder.subgraph;

import com.zhouruojun.jobsearchagent.agent.builder.BaseSubgraphBuilder;
import com.zhouruojun.jobsearchagent.agent.actions.CallSubAgent;
import com.zhouruojun.jobsearchagent.agent.actions.ExecuteTools;
import com.zhouruojun.jobsearchagent.agent.serializers.AgentSerializers;
import com.zhouruojun.jobsearchagent.agent.state.SubgraphState;
import com.zhouruojun.jobsearchagent.common.PromptTemplateManager;
import com.zhouruojun.jobsearchagent.tools.JobSearchToolCollection;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.EdgeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import java.util.Map;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;

/**
 * 岗位信息收集子图构建器
 * 专门处理岗位信息收集相关的工具调用
 * 职责：多平台岗位搜索、信息抓取、标准化
 */
public class JobInfoCollectionSubgraphBuilder extends BaseSubgraphBuilder<SubgraphState> {

    @Override
    protected String getAgentName() {
        return "jobInfoCollectorAgent";
    }

    @Override
    protected String getActionName() {
        return "jobInfoCollectionAction";
    }


    @Override
    protected String getPrompt() {
        return PromptTemplateManager.getInstance().getJobInfoCollectionPrompt();
    }

    @Override
    protected JobSearchToolCollection createTempToolCollection() {
        return JobSearchToolCollection.createSubgraphToolCollection(getAgentName(), mainToolCollection);
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
        
        EdgeAction<SubgraphState> agentShouldContinue = getStandardAgentShouldContinue();
        EdgeAction<SubgraphState> actionShouldContinue = getStandardActionShouldContinue();
        
        return new StateGraph<>(MessagesState.SCHEMA, stateSerializer)
                .addNode(getAgentName(), node_async(callAgent))
                .addNode(getActionName(), node_async(executeTools))
                .addEdge(START, getAgentName())
                .addConditionalEdges(getAgentName(),
                        edge_async(agentShouldContinue),
                        Map.of(
                                "action", getActionName(),
                                "END", END))
                .addConditionalEdges(getActionName(),
                        edge_async(actionShouldContinue),
                        Map.of("callback", getAgentName()));
    }
}
