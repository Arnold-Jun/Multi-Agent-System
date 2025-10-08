package com.xiaohongshu.codewiz.codewizagent.cragent.agent.builder;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

import com.xiaohongshu.codewiz.codewizagent.cragent.agent.BaseAgent;
import com.xiaohongshu.codewiz.codewizagent.cragent.agent.actions.CallAgent;
import com.xiaohongshu.codewiz.codewizagent.cragent.agent.actions.ExecuteToolWaiting;
import com.xiaohongshu.codewiz.codewizagent.cragent.agent.actions.ExecuteTools;
import com.xiaohongshu.codewiz.codewizagent.cragent.agent.actions.HumanConfirm;
import com.xiaohongshu.codewiz.codewizagent.cragent.agent.actions.HumanPostConfirm;
import com.xiaohongshu.codewiz.codewizagent.cragent.agent.actions.UserInput;
import com.xiaohongshu.codewiz.codewizagent.cragent.agent.serializers.AgentSerializers;
import com.xiaohongshu.codewiz.codewizagent.cragent.agent.state.AgentMessageState;
import com.xiaohongshu.codewiz.codewizagent.cragent.common.PromptTemplate;
import com.xiaohongshu.codewiz.codewizagent.cragent.mcp.ToolProviderManager;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import java.util.ArrayList;
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
    private String skill;

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
     * Sets the skill for the graph builder.
     * @param skill
     * @return
     */
    public AgentGraphBuilder skill(String skill) {
        this.skill = skill;
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

        var agent = BaseAgent.builder()
                .chatLanguageModel(chatLanguageModel)
                .streamingChatLanguageModel(streamingChatLanguageModel)
                .tools(toolProviderManager.toolSpecifications(username, requestId))
                .prompt(PromptTemplate.getInstance().get("crAgent"))
                .build();

        if (stateSerializer == null) {
            stateSerializer = AgentSerializers.STD.object();
        }

        List<String> supervisorChildren = new ArrayList<>();
        supervisorChildren.add("crAgent");
        supervisorChildren.add("userInput");

        BlockingQueue<AsyncGenerator.Data<StreamingOutput<AgentMessageState>>> queue = new LinkedBlockingQueue<>();

        final var callAgent = new CallAgent("crAgent", agent);
        callAgent.setQueue(queue);
        final var executeTools = new ExecuteTools("crAction", agent, toolProviderManager);

//        final EdgeAction<AgentMessageState> crAgentShouldContinue = (state) ->
//                state.finalResponse()
//                        .map(res -> "FINISH")
//                        .orElse("action");

        final EdgeAction<AgentMessageState> crAgentShouldContinue = (state) ->
                state.toolInterceptor()
                        .filter(StringUtils::isNoneEmpty)
                        .map( res -> "human")
                        .orElse(state.finalResponse()
                                .filter(StringUtils::isNoneEmpty)
                                        // 这里的res是CrAgent的返回结果，当结果里包含FINISH时，才真正结束，如果不包含，那么都进入用户输入阶段
                                        .map(res -> res.contains("FINISH") ? "FINISH" : "userInput")
                                        .orElse("action"));

        final EdgeAction<AgentMessageState> humanConfirmShouldContinue = (state) ->
                state.next().orElse("FINISH");

        final EdgeAction<AgentMessageState> actionWaitingShouldContinue = (state) ->
                state.next().orElse("callback");

        // todo skill 区分当前使用的流程与agent
        if ("create_cr".equals(skill)) {
            return new StateGraph<>(AgentMessageState.SCHEMA, stateSerializer)
                    .addNode("crAgent", node_async(callAgent))
                    .addNode("crAction", node_async(executeTools))
                    .addNode("actionWaiting", node_async(new ExecuteToolWaiting()))
                    .addNode("humanConfirm", node_async(new HumanConfirm()))
                    .addNode("humanPostConfirm", node_async(new HumanPostConfirm()))
                    .addNode("userInput", node_async(new UserInput()))
                    .addEdge(START, "crAgent")
                    .addConditionalEdges("crAgent",
                            edge_async(crAgentShouldContinue),
                            Map.of(
                                    "userInput", "userInput", // 用于用户输入确认
                                    "human", "humanConfirm", // 工具的前置拦截，用于用户确认继续往下执行
                                    "action", "crAction", "FINISH", END))
                    .addConditionalEdges("crAction",
                            edge_async(actionWaitingShouldContinue),
                            Map.of(
                                    "human","humanPostConfirm", // 工具的后置拦截，用于结果确认与修改
                                    "actionWaiting","actionWaiting", "callback", "crAgent")
                    )
                    .addConditionalEdges("humanConfirm",
                            edge_async(humanConfirmShouldContinue),
                            Map.of("action", "crAction", "callback","crAgent", "FINISH", END)
                    )
                    .addConditionalEdges("humanPostConfirm",
                            edge_async(humanConfirmShouldContinue),
                            Map.of("action", "crAction", "callback","crAgent", "FINISH", END)
                    )
                    .addEdge("actionWaiting", "crAgent")
                    .addEdge("userInput", "crAgent")
//                .addEdge("crAction", "crAgent")
                    ;
        } else {
            return new StateGraph<>(AgentMessageState.SCHEMA, stateSerializer)
                    .addNode("crAgent", node_async(callAgent))
                    .addNode("crAction", node_async(executeTools))
                    .addNode("actionWaiting", node_async(new ExecuteToolWaiting()))
                    .addNode("humanConfirm", node_async(new HumanConfirm()))
                    .addNode("humanPostConfirm", node_async(new HumanPostConfirm()))
                    .addNode("userInput", node_async(new UserInput()))
                    .addEdge(START, "crAgent")
                    .addConditionalEdges("crAgent",
                            edge_async(crAgentShouldContinue),
                            Map.of(
                                    "userInput", "userInput", // 用于用户输入确认
                                    "human", "humanConfirm", // 工具的前置拦截，用于用户确认继续往下执行
                                    "action", "crAction", "FINISH", END))
                    .addConditionalEdges("crAction",
                            edge_async(actionWaitingShouldContinue),
                            Map.of(
                                    "human","humanPostConfirm", // 工具的后置拦截，用于结果确认与修改
                                    "actionWaiting","actionWaiting", "callback", "crAgent")
                    )
                    .addConditionalEdges("humanConfirm",
                            edge_async(humanConfirmShouldContinue),
                            Map.of("action", "crAction", "callback","crAgent", "FINISH", END)
                    )
                    .addConditionalEdges("humanPostConfirm",
                            edge_async(humanConfirmShouldContinue),
                            Map.of("action", "crAction", "callback","crAgent", "FINISH", END)
                    )
                    .addEdge("actionWaiting", "crAgent")
                    .addEdge("userInput", "crAgent");
        }

    }
}