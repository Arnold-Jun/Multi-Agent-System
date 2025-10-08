package com.xiaohongshu.codewiz.codewizagent.cragent.agent.actions;

import cn.hutool.core.collection.CollectionUtil;
import com.alibaba.fastjson.JSONObject;
import com.xiaohongshu.codewiz.codewizagent.cragent.agent.BaseAgent;
import com.xiaohongshu.codewiz.codewizagent.cragent.agent.state.AgentMessageState;
import com.xiaohongshu.codewiz.codewizagent.cragent.common.ApplicationContextUtil;
import com.xiaohongshu.codewiz.codewizagent.cragent.mcp.ToolInterceptor;
import com.xiaohongshu.codewiz.codewizagent.cragent.mcp.ToolProviderManager;
import com.xiaohongshu.codewiz.codewizagent.cragent.mcp.ToolsInterceptorUtil;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageType;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.action.NodeAction;
import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver;
import org.bsc.langgraph4j.checkpoint.Checkpoint;
import org.bsc.langgraph4j.checkpoint.MemorySaver;

/**
 * The ExecuteTools class implements the NodeAction interface for handling 
 * actions related to executing tools within an agent's context.
 *
 * @author 瑞诺
 * create on 2025/4/10 11:38
 */
@Slf4j
public class ExecuteTools implements NodeAction<AgentMessageState> {

    /**
     * The agent associated with this execution tool.
     */
    final BaseAgent agent;

    final String agentName;

    /**
     * The tool node that will be executed.
     */
    private ToolProviderManager toolProviderManager;


    /**
     * MCP provider for tools
     */

    /**
     * Constructs an ExecuteTools instance with the specified agent and tool node.
     *
     * @param agent the agent to be associated with this execution tool, must not be null
     * @param toolProviderManager the tool node to be executed, must not be null
     */
    public ExecuteTools(@NonNull String agentName,
                        @NonNull BaseAgent agent,
                        @NonNull ToolProviderManager toolProviderManager) {
        this.agentName = agentName;
        this.agent = agent;
        this.toolProviderManager = toolProviderManager;
    }

    /**
     * Applies the tool execution logic based on the provided agent state.
     *
     * @param state the current state of the agent executor
     * @return a map containing the intermediate steps of the execution
     * @throws IllegalArgumentException if no agent outcome is provided
     * @throws IllegalStateException if no action or tool is found for execution
     */
    @Override
    public Map<String,Object> apply (AgentMessageState state )  {
        log.info( "executeTools" );

        String sessionId = state.getSessionId().get();
        String requestId = state.getRequestId().get();
        String username = state.getUsername().get();

        List<ToolExecutionRequest> toolExecutionRequests = null;
        Optional<ChatMessage> chatMessage = state.lastMessage();
        if (ChatMessageType.AI == chatMessage.get().type()) {
            toolExecutionRequests = state.lastMessage()
                    .filter( m -> ChatMessageType.AI==m.type() )
                    .map( m -> (AiMessage)m )
                    .filter(AiMessage::hasToolExecutionRequests)
                    .map(AiMessage::toolExecutionRequests)
                    .orElseThrow(() -> new IllegalArgumentException("no tool execution request found!"));
        } else {
            List<ChatMessage> messages = state.messages();
            int aiCount = 0;
            for (int i = messages.size() - 1; i > 0; i--) {
                if (aiCount > 0) {
                    break;
                }
                if (ChatMessageType.AI == messages.get(i).type()) {
                    aiCount++;
                    AiMessage aiMessage = (AiMessage) messages.get(i);
                    if (aiMessage.hasToolExecutionRequests()) {
                        toolExecutionRequests = aiMessage.toolExecutionRequests();
                    }
                }
            }
        }

        if (CollectionUtil.isEmpty(toolExecutionRequests)) {
            throw new IllegalArgumentException("no tool execution request found!");
        }

        var result = toolExecutionRequests.stream()
                        .map(toolExecutionRequest -> toolProviderManager.execute(toolExecutionRequest, sessionId ,username, requestId))
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .toList();

        if ("STOP".equals(result.get(0).text())) {
            return Map.of( "next", "actionWaiting", "tool_interceptor", "", "tool_post_interceptor", "");
        }
        ToolInterceptor postInterceptor = ToolsInterceptorUtil.getPostInterceptor(result.get(0).toolName());
        if (postInterceptor != null) {
            postInterceptor.setToolInfo(result.get(0).text());
            postInterceptor.setRequestId(requestId);
            postInterceptor.setSessionId(sessionId);
            postInterceptor.setId(result.get(0).id());
            return Map.of( "next", "human", "tool_interceptor", "", "tool_post_interceptor", JSONObject.toJSONString(postInterceptor));
        }

        return Map.of("next", "callback", "messages", result, "tool_interceptor", "" , "tool_post_interceptor", "");

    }

}
