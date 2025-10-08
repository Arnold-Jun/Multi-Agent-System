package com.xiaohongshu.codewiz.codewizagent.agentcore.agent.builder;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

import cn.hutool.core.collection.CollectionUtil;
import com.xiaohongshu.codewiz.a2acore.spec.AgentCard;
import com.xiaohongshu.codewiz.codewizagent.agentcore.a2a.A2aClientManager;
import com.xiaohongshu.codewiz.codewizagent.agentcore.agent.BaseAgent;
import com.xiaohongshu.codewiz.codewizagent.agentcore.agent.SupervisorAgent;
import com.xiaohongshu.codewiz.codewizagent.agentcore.agent.actions.AgentInvoke;
import com.xiaohongshu.codewiz.codewizagent.agentcore.agent.actions.AgentInvokeStateCheck;
import com.xiaohongshu.codewiz.codewizagent.agentcore.agent.actions.CallAgent;
import com.xiaohongshu.codewiz.codewizagent.agentcore.agent.actions.CallSupervisorAgent;
import com.xiaohongshu.codewiz.codewizagent.agentcore.agent.actions.ExecuteToolWaiting;
import com.xiaohongshu.codewiz.codewizagent.agentcore.agent.actions.ExecuteTools;
import com.xiaohongshu.codewiz.codewizagent.agentcore.agent.actions.HumanConfirm;
import com.xiaohongshu.codewiz.codewizagent.agentcore.agent.actions.HumanPostConfirm;
import com.xiaohongshu.codewiz.codewizagent.agentcore.agent.actions.UserInput;
import com.xiaohongshu.codewiz.codewizagent.agentcore.agent.serializers.AgentSerializers;
import com.xiaohongshu.codewiz.codewizagent.agentcore.agent.state.AgentMessageState;
import com.xiaohongshu.codewiz.codewizagent.agentcore.common.PromptTemplate;
import com.xiaohongshu.codewiz.codewizagent.agentcore.mcp.ToolProviderManager;
import com.xiaohongshu.codewiz.codewizagent.agentcore.scene.SceneAgentConfig;
import com.xiaohongshu.codewiz.codewizagent.agentcore.scene.SceneConfig;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import org.apache.commons.lang3.StringUtils;
import org.bsc.async.AsyncGenerator;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.EdgeAction;
import org.bsc.langgraph4j.serializer.StateSerializer;
import org.bsc.langgraph4j.streaming.StreamingOutput;

/**
 * Builder class for constructing a graph of agent execution.
 */
public class AgentGraphBuilder {
    private StreamingChatLanguageModel streamingChatLanguageModel;
    private ChatLanguageModel chatLanguageModel;
    private StateSerializer<AgentMessageState> stateSerializer;
    private ToolProviderManager toolProviderManager;
    private String username;
    private String requestId;
    private A2aClientManager a2aClientManager;
    private SceneConfig sceneConfig;

    /**
     * Sets the chat language model for the graph builder.
     *
     * @param chatLanguageModel the chat language model
     * @return the updated GraphBuilder instance
     */
    public AgentGraphBuilder chatLanguageModel(ChatLanguageModel chatLanguageModel) {
        this.chatLanguageModel = chatLanguageModel;
        return this;
    }

    /**
     * Sets the streaming chat language model for the graph builder.
     *
     * @param streamingChatLanguageModel the streaming chat language model
     * @return the updated GraphBuilder instance
     */
    public AgentGraphBuilder chatLanguageModel(StreamingChatLanguageModel streamingChatLanguageModel) {
        this.streamingChatLanguageModel = streamingChatLanguageModel;
        return this;
    }


    /**
     * Sets the state serializer for the graph builder.
     *
     * @param stateSerializer the state serializer
     * @return the updated GraphBuilder instance
     */
    public AgentGraphBuilder stateSerializer(StateSerializer<AgentMessageState> stateSerializer) {
        this.stateSerializer = stateSerializer;
        return this;
    }

    /**
     * Sets the tool provider for the graph builder.
     * @param toolProviderManager
     * @return
     */
    public AgentGraphBuilder toolProviderManager(ToolProviderManager toolProviderManager) {
        this.toolProviderManager = toolProviderManager;
        return this;
    }

    /**
     * Sets the user name for the graph builder.
     * @param username
     * @return
     */
    public AgentGraphBuilder username(String username) {
        this.username = username;
        return this;
    }

    /**
     * Sets the requestId for the graph builder.
     * @param requestId
     * @return
     */
    public AgentGraphBuilder requestId(String requestId) {
        this.requestId = requestId;
        return this;
    }

    /**
     * Sets the A2aClientManager for the graph builder.
     * @param a2aClientManager
     * @return
     */
    public AgentGraphBuilder a2aClientManager(A2aClientManager a2aClientManager) {
        this.a2aClientManager = a2aClientManager;
        return this;
    }

    /**
     * Sets the scene configuration for the graph builder.
     * @param sceneConfig
     * @return
     */
    public AgentGraphBuilder sceneConfig(SceneConfig sceneConfig) {
        this.sceneConfig = sceneConfig;
        return this;
    }

    /**
     * Builds the state graph.
     *
     * @return the constructed StateGraph
     * @throws GraphStateException if there is an error in the graph state
     */
    public StateGraph<AgentMessageState> build() throws GraphStateException {

        if (streamingChatLanguageModel != null && chatLanguageModel != null) {
            throw new IllegalArgumentException("chatLanguageModel and streamingChatLanguageModel are mutually exclusive!");
        }
        if (streamingChatLanguageModel == null && chatLanguageModel == null) {
            throw new IllegalArgumentException("a chatLanguageModel or streamingChatLanguageModel is required!");
        }

//
//        var agent = BaseAgent.builder()
//                .chatLanguageModel(chatLanguageModel)
//                .streamingChatLanguageModel(streamingChatLanguageModel)
//                .tools(toolProviderManager.toolSpecifications(username, requestId))
//                .prompt(PromptTemplate.getInstance().get("crAgent"))
//                .build();

        if (stateSerializer == null) {
            stateSerializer = AgentSerializers.STD.object();
        }

        List<AgentCard> agentCards;
        if (!CollectionUtil.isEmpty(sceneConfig.getSceneAgentConfigs())) {
            List<String> a2aChildren = sceneConfig.getSceneAgentConfigs().stream().map(SceneAgentConfig::getName).toList();
            agentCards = a2aClientManager.getAgentCards().stream().filter(agentCard -> a2aChildren.contains(agentCard.getName())).toList();
        } else {
            agentCards = Collections.emptyList();
        }

        var supervisorAgent = new SupervisorAgent(
                chatLanguageModel,
                streamingChatLanguageModel,
                null,
//                PromptTemplate.getInstance().get("supervisor")
                "supervisor",
                agentCards
                );

        List<String> supervisorChildren = new ArrayList<>();
        supervisorChildren.add("userInput");
        supervisorChildren.add("agentInvoke");

        final EdgeAction<AgentMessageState> supervisorRouter = (state) ->
                state.next().filter(supervisorChildren::contains).orElse("userInput");

        BlockingQueue<AsyncGenerator.Data<StreamingOutput<AgentMessageState>>> queue = new LinkedBlockingQueue<>();

        final var callSupervisorAgent = new CallSupervisorAgent("supervisor", supervisorAgent, supervisorChildren, requestId, username);
        callSupervisorAgent.setQueue(queue);

        return new StateGraph<>(AgentMessageState.SCHEMA, stateSerializer)
                .addNode("supervisor", node_async(callSupervisorAgent))
                .addNode("agentInvoke", node_async(new AgentInvoke(a2aClientManager, toolProviderManager.toolSpecifications(username, requestId))))
                .addNode("agentInvokeStateCheck", node_async(new AgentInvokeStateCheck(a2aClientManager)))
                .addNode("userInput", node_async(new UserInput()))
                .addEdge(START, "supervisor")
                .addConditionalEdges("supervisor",
                        edge_async(supervisorRouter),
                        Map.of(
                                "agentInvoke", "agentInvoke",
                                "userInput","userInput",
                                "FINISH", END
                        ))
                .addConditionalEdges("agentInvoke",
                        edge_async(state ->
                                state.next().orElse("FINISH")),
                        Map.of(
                                "agentInvokeStateCheck","agentInvokeStateCheck",
                                "FINISH", "supervisor"
                        ))
                .addConditionalEdges("agentInvokeStateCheck",
                        edge_async(state ->
                                state.next().orElse("FINISH")),
                        Map.of(
                                "continue","agentInvokeStateCheck",
                                "subscribe", "agentInvoke",
                                "FINISH", "supervisor"
                        ))
                .addEdge("userInput", "supervisor")
                ;
    }
}