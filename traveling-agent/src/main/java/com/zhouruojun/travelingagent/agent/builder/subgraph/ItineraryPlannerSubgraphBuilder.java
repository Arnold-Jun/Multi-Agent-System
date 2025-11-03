package com.zhouruojun.travelingagent.agent.builder.subgraph;

import com.zhouruojun.travelingagent.agent.BaseAgent;
import com.zhouruojun.travelingagent.agent.actions.CallPlannerAgentForItinerary;
import com.zhouruojun.travelingagent.agent.actions.CallSupervisorAgentForItinerary;
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
 * 双Agent迭代架构：
 * 1. PlannerAgent: 负责制定旅游行程和机酒方案
 * 2. SupervisorAgent: 负责监督方案的合理性，检查时间冲突、不一致等问题
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
        // 注意：虽然子图名称是"itineraryPlannerAgent"，但内部的planner agent名称是"itineraryPlanner"
        // PromptProvider注册时使用的是"itineraryPlanner"
        // 这里返回"itineraryPlanner"的prompt，但实际上这个agent不会被使用（因为我们重写了buildGraph）
        // 保持一致性，使用实际agent的名称
        return promptManager.getSubgraphSystemPrompt("itineraryPlanner");
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
            com.zhouruojun.travelingagent.agent.actions.CallSubAgent callAgent, 
            ExecuteTools<SubgraphState> executeTools) throws GraphStateException {
        log.info("构建 ItineraryPlannerAgent 子图（双Agent迭代架构）...");

        // 创建Planner Agent和Supervisor Agent
        BaseAgent itineraryPlanner = createItineraryPlanner();
        BaseAgent itinerarySupervisor = createItinerarySupervisor();

        // 创建专用的节点Action
        CallPlannerAgentForItinerary plannerNode = new CallPlannerAgentForItinerary(
                "itineraryPlanner", itineraryPlanner, promptManager);
        CallSupervisorAgentForItinerary supervisorNode = new CallSupervisorAgentForItinerary(
                "itinerarySupervisor", itinerarySupervisor, promptManager);

        // 创建ExecuteTools节点（为itineraryPlanner使用）
        ExecuteTools<SubgraphState> plannerExecuteTools = new ExecuteTools<>(
                "itineraryPlanner", itineraryPlanner, toolProviderManager, parallelExecutionConfig);

        // 获取路由逻辑
        EdgeAction<SubgraphState> plannerRouting = this::plannerRouting;
        EdgeAction<SubgraphState> supervisorRouting = this::supervisorRouting;
        EdgeAction<SubgraphState> actionShouldContinue = getStandardActionShouldContinue();

        // 构建双Agent迭代图
        return new StateGraph<>(SubgraphState.SCHEMA, stateSerializer)
                // 节点定义
                .addNode("itineraryPlanner", node_async(plannerNode))
                .addNode("executeTools", node_async(plannerExecuteTools))
                .addNode("itinerarySupervisor", node_async(supervisorNode))
                
                // 边连接
                .addEdge(START, "itineraryPlanner")
                
                // Planner路由：可能需要工具调用，或直接生成方案
                .addConditionalEdges("itineraryPlanner",
                        edge_async(plannerRouting),
                        Map.of(
                                "executeTools", "executeTools",
                                "itinerarySupervisor", "itinerarySupervisor"))
                
                // 工具执行后回调到Planner
                .addConditionalEdges("executeTools",
                        edge_async(actionShouldContinue),
                        Map.of("callback", "itineraryPlanner"))
                
                // Supervisor路由：如果需要修改返回Planner，如果通过则结束
                .addConditionalEdges("itinerarySupervisor",
                        edge_async(supervisorRouting),
                        Map.of(
                                "itineraryPlanner", "itineraryPlanner",
                                "END", END));
    }

    /**
     * 创建ItineraryPlanner Agent（使用itineraryPlanner名称获取工具）
     */
    private BaseAgent createItineraryPlanner() {
        if (toolProviderManager != null) {
            return BaseAgent.builder()
                    .chatLanguageModel(chatLanguageModel)
                    .streamingChatLanguageModel(streamingChatLanguageModel)
                    .tools(toolProviderManager.getToolSpecificationsByAgent("itineraryPlanner"))
                    .agentName("itineraryPlanner")
                    .prompt(promptManager.getSubgraphSystemPrompt("itineraryPlanner"))
                    .build();
        }
        
        return BaseAgent.builder()
                .chatLanguageModel(chatLanguageModel)
                .streamingChatLanguageModel(streamingChatLanguageModel)
                .agentName("itineraryPlanner")
                .prompt(promptManager.getSubgraphSystemPrompt("itineraryPlanner"))
                .build();
    }

    /**
     * 创建ItinerarySupervisor Agent（不需要工具，只负责审查）
     */
    private BaseAgent createItinerarySupervisor() {
        // Supervisor不需要工具，只负责审查方案
        return BaseAgent.builder()
                .chatLanguageModel(chatLanguageModel)
                .streamingChatLanguageModel(streamingChatLanguageModel)
                .agentName("itinerarySupervisor")
                .prompt(promptManager.getSubgraphSystemPrompt("itinerarySupervisor"))
                .build();
    }

    /**
     * Planner路由逻辑：判断是否需要工具调用
     */
    private String plannerRouting(SubgraphState state) {
        // 如果Planner返回的next是"executeTools"，说明需要工具调用
        String next = state.next().orElse("itinerarySupervisor");
        log.debug("ItineraryPlanner路由决策: next={}", next);
        return next;
    }

    /**
     * Supervisor路由逻辑：判断是否需要修改方案
     */
    private String supervisorRouting(SubgraphState state) {
        // 检查是否需要修改
        boolean needsRevision = state.needsRevision();
        boolean maxIterations = state.isMaxIterationsReached();
        
        if (maxIterations) {
            log.info("达到最大迭代次数，强制结束");
            return "END";
        }
        
        if (needsRevision) {
            log.info("ItinerarySupervisor发现需要修改，返回ItineraryPlanner进行修改");
            return "itineraryPlanner";
        } else {
            log.info("ItinerarySupervisor审查通过，结束迭代");
            return "END";
        }
    }
}
