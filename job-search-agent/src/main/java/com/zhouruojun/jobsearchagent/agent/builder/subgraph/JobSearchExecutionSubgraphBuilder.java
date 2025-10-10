package com.zhouruojun.jobsearchagent.agent.builder.subgraph;

import com.zhouruojun.jobsearchagent.agent.builder.BaseSubgraphBuilder;
import com.zhouruojun.jobsearchagent.agent.actions.CallSubAgent;
import com.zhouruojun.jobsearchagent.agent.actions.CallSubSummaryAgent;
import com.zhouruojun.jobsearchagent.agent.actions.ExecuteTools;
import com.zhouruojun.jobsearchagent.agent.serializers.AgentSerializers;
import com.zhouruojun.jobsearchagent.agent.state.MainGraphState;
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
 * 求职执行子图构建器
 * 专门处理求职策略制定和岗位投递相关的工具调用
 * 职责：策略制定、岗位投递、投递结果跟踪、策略调整建议
 */
public class JobSearchExecutionSubgraphBuilder extends BaseSubgraphBuilder<SubgraphState> {

    @Override
    protected String getAgentName() {
        return "jobSearchExecutionAgent";
    }

    @Override
    protected String getActionName() {
        return "jobSearchExecutionAction";
    }

    @Override
    protected String getSummaryName() {
        return "jobSearchExecutionSummary";
    }

    @Override
    protected String getPrompt() {
        return PromptTemplateManager.getInstance().getJobSearchExecutionPrompt();
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

        // 创建子图总结智能体
        CallSubSummaryAgent callSubSummaryAgent = new CallSubSummaryAgent("subSummaryAgent", createAgent());

        EdgeAction<SubgraphState> agentShouldContinue = getStandardAgentShouldContinue();
        EdgeAction<SubgraphState> actionShouldContinue = getStandardActionShouldContinue();
        
        // 构建包含总结节点的状态图
        return new StateGraph<>(MessagesState.SCHEMA, stateSerializer)
                .addNode(getAgentName(), node_async(callAgent))
                .addNode(getActionName(), node_async(executeTools))
                .addNode(getSummaryName(), node_async(callSubSummaryAgent))
                .addEdge(START, getAgentName())
                .addConditionalEdges(getAgentName(),
                        edge_async(agentShouldContinue),
                        Map.of(
                                "action", getActionName(),
                                "summary", getSummaryName()))
                .addConditionalEdges(getActionName(),
                        edge_async(actionShouldContinue),
                        Map.of("callback", getAgentName()))
                .addEdge(getSummaryName(), END);

    }
}
