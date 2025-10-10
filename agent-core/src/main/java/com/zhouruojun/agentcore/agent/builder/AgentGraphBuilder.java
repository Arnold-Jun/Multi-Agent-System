package com.zhouruojun.agentcore.agent.builder;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

import com.zhouruojun.agentcore.agent.SupervisorAgent;
import com.zhouruojun.agentcore.a2a.A2aClientManager;
import com.zhouruojun.a2acore.spec.AgentCard;
import com.zhouruojun.agentcore.agent.actions.AgentInvoke;
import com.zhouruojun.agentcore.agent.actions.AgentInvokeStateCheck;
import com.zhouruojun.agentcore.agent.actions.CallSupervisorAgent;
import com.zhouruojun.agentcore.agent.actions.UserInput;
import com.zhouruojun.agentcore.agent.serializers.AgentSerializers;
import com.zhouruojun.agentcore.agent.state.AgentMessageState;
import com.zhouruojun.agentcore.mcp.ToolProviderManager;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import org.bsc.async.AsyncGenerator;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.EdgeAction;
import org.bsc.langgraph4j.serializer.StateSerializer;
import org.bsc.langgraph4j.streaming.StreamingOutput;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.stream.Collectors;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Agent图构建器
 * 使用LangGraph4j构建智能体执行图
 * 

 */
@Slf4j
public class AgentGraphBuilder {
    private StreamingChatLanguageModel streamingChatLanguageModel;
    private ChatLanguageModel chatLanguageModel;
    private StateSerializer<AgentMessageState> stateSerializer;
    private ToolProviderManager toolProviderManager;
    private String username;
    private String requestId;
    private A2aClientManager a2aClientManager;

    /**
     * 设置聊天语言模型
     */
    public AgentGraphBuilder chatLanguageModel(ChatLanguageModel chatLanguageModel) {
        this.chatLanguageModel = chatLanguageModel;
        return this;
    }

    /**
     * 设置流式聊天语言模型
     */
    public AgentGraphBuilder chatLanguageModel(StreamingChatLanguageModel streamingChatLanguageModel) {
        this.streamingChatLanguageModel = streamingChatLanguageModel;
        return this;
    }

    /**
     * 设置状态序列化器
     */
    public AgentGraphBuilder stateSerializer(StateSerializer<AgentMessageState> stateSerializer) {
        this.stateSerializer = stateSerializer;
        return this;
    }

    /**
     * 设置工具提供者管理器
     */
    public AgentGraphBuilder toolProviderManager(ToolProviderManager toolProviderManager) {
        this.toolProviderManager = toolProviderManager;
        return this;
    }

    /**
     * 设置用户名
     */
    public AgentGraphBuilder username(String username) {
        this.username = username;
        return this;
    }

    /**
     * 设置请求ID
     */
    public AgentGraphBuilder requestId(String requestId) {
        this.requestId = requestId;
        return this;
    }

    /**
     * 设置A2A客户端管理器
     */
    public AgentGraphBuilder a2aClientManager(A2aClientManager a2aClientManager) {
        this.a2aClientManager = a2aClientManager;
        return this;
    }



    /**
     * 构建状态图
     */
    public StateGraph<AgentMessageState> build() throws GraphStateException {
        
        // 验证必要参数
        if (streamingChatLanguageModel != null && chatLanguageModel != null) {
            throw new IllegalArgumentException("chatLanguageModel and streamingChatLanguageModel are mutually exclusive!");
        }
        if (streamingChatLanguageModel == null && chatLanguageModel == null) {
            throw new IllegalArgumentException("a chatLanguageModel or streamingChatLanguageModel is required!");
        }

        // 设置默认序列化器
        if (stateSerializer == null) {
            stateSerializer = AgentSerializers.STD.object();
        }

        // 获取可用的智能体信息
        List<AgentCard> agentCards = new ArrayList<>();
        
        if (a2aClientManager != null) {
            try {
                agentCards = a2aClientManager.getAgentCards();
                if (agentCards != null && !agentCards.isEmpty()) {
                    List<String> agentNames = agentCards.stream()
                        .map(card -> card.getName())
                        .collect(Collectors.toList());
                    log.info("Found {} available agents: {}", agentNames.size(), agentNames);
                } else {
                    log.warn("No agent cards found, no agents available");
                }
            } catch (Exception e) {
                log.error("Error getting agent cards, no agents available", e);
            }
        } else {
            log.warn("A2aClientManager is null, no agents available");
        }

        // 创建主管智能体
        var supervisorAgent = SupervisorAgent.supervisorBuilder()
                .chatLanguageModel(chatLanguageModel)
                .streamingChatLanguageModel(streamingChatLanguageModel)
                .agentName("supervisor")
                .agentCards(agentCards)
                .build();

        // 创建子节点列表
        List<String> supervisorChildren = new ArrayList<>();
        supervisorChildren.add("userInput");
        supervisorChildren.add("agentInvoke");

        // 定义主管路由逻辑
        final EdgeAction<AgentMessageState> supervisorRouter = (state) ->
                state.next().filter(supervisorChildren::contains).orElse("userInput");

        // 创建流式输出队列
        BlockingQueue<AsyncGenerator.Data<StreamingOutput<AgentMessageState>>> queue = new LinkedBlockingQueue<>();

        // 创建主管调用节点
        final var callSupervisorAgent = new CallSupervisorAgent("supervisor", supervisorAgent, username);
        callSupervisorAgent.setQueue(queue);

        // 构建状态图
        return new StateGraph<AgentMessageState>(AgentMessageState.SCHEMA, stateSerializer)
                // 添加节点
                .addNode("supervisor", node_async(callSupervisorAgent))
                .addNode("agentInvoke", node_async(new AgentInvoke(a2aClientManager, toolProviderManager.toolSpecifications(username, requestId))))
                .addNode("agentInvokeStateCheck", node_async(new AgentInvokeStateCheck(a2aClientManager)))
                .addNode("userInput", node_async(new UserInput()))
                
                // 添加边
                .addEdge(START, "supervisor")
                
                // 添加条件边 - 主管决策
                .addConditionalEdges("supervisor",
                        edge_async(supervisorRouter),
                        Map.of(
                                "agentInvoke", "agentInvoke",
                                "userInput", "userInput",
                                "FINISH", END
                        ))
                
                // 添加条件边 - 智能体调用后的路由
                .addConditionalEdges("agentInvoke",
                        edge_async(state -> state.next().orElse("supervisor")),
                        Map.of(
                                "agentInvokeStateCheck", "agentInvokeStateCheck",  // ✅ 添加缺失的映射
                                "supervisor", "supervisor",
                                "userInput", "userInput"
                        ))
                
                // 添加条件边 - 智能体调用状态检查
                .addConditionalEdges("agentInvokeStateCheck",
                        edge_async(state -> state.next().orElse("FINISH")),
                        Map.of(
                                "continue", "agentInvokeStateCheck",
                                "FINISH", "supervisor"
                        ))
                
                // 用户输入后回到主管
                .addEdge("userInput", "supervisor");
    }
}
