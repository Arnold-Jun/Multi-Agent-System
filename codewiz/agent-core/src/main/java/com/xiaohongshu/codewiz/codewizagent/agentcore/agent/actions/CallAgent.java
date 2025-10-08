package com.xiaohongshu.codewiz.codewizagent.agentcore.agent.actions;

import com.alibaba.fastjson.JSONObject;
import com.xiaohongshu.codewiz.codewizagent.agentcore.agent.BaseAgent;
import com.xiaohongshu.codewiz.codewizagent.agentcore.agent.state.AgentMessageState;
import com.xiaohongshu.codewiz.codewizagent.agentcore.mcp.ToolInterceptor;
import com.xiaohongshu.codewiz.codewizagent.agentcore.mcp.ToolsInterceptorUtil;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.bsc.async.AsyncGenerator;
import org.bsc.langgraph4j.action.NodeAction;
import org.bsc.langgraph4j.langchain4j.generators.StreamingChatGenerator;
import org.bsc.langgraph4j.streaming.StreamingOutput;

/**
 * The CallAgent class implements the NodeAction interface for handling 
 * actions related to an AgentExecutor's state.
 */
@Slf4j
public class CallAgent implements NodeAction<AgentMessageState> {

    final BaseAgent agent;

    final String agentName;

    StreamingChatGenerator<AgentMessageState> generator = null;

    BlockingQueue<AsyncGenerator.Data<StreamingOutput<AgentMessageState>>> queue = null;

    private String username;

    private String requestId;

    /**
     * Constructs a CallAgent with the specified agent.
     *
     * @param agent the agent to be associated with this CallAgent
     */
    public CallAgent( String agentName,  BaseAgent agent, String username, String requestId ) {
        this.agent = agent;
        this.agentName = agentName;
        this.username = username;
        this.requestId = requestId;
    }

    public void setQueue(
            BlockingQueue<AsyncGenerator.Data<StreamingOutput<AgentMessageState>>> queue) {
        this.queue = queue;
    }

    /**
     * Maps the result of the response from an AI message to a structured format.
     *
     * @param response the response containing the AI message
     * @return a map containing the agent's outcome
     * @throws IllegalStateException if the finish reason of the response is unsupported
     */
    private Map<String,Object> mapResult( ChatResponse response )  {

        var content = response.aiMessage();

        if( response.finishReason() == FinishReason.STOP ) {
            AiMessage aiMessage = content;
            String text = content.text();
            if (StringUtils.isNoneEmpty(text)) {
//                JSONObject textJSON = JSONObject.parseObject(text);
//                textJSON.put("requestId", requestId);
                Map<String, String> map = Map.of("requestId", requestId, "context", text);
                aiMessage = AiMessage.from(JSONObject.toJSONString(map));
            }
            return Map.of("agent_response", content.text(),
                    "messages", aiMessage, "tool_interceptor", "", "tool_post_interceptor", "");
        }

        if (response.finishReason() == FinishReason.TOOL_EXECUTION || content.hasToolExecutionRequests() ) {
            ToolExecutionRequest toolExecutionRequest = content.toolExecutionRequests().get(0);
            ToolInterceptor toolInterceptor = ToolsInterceptorUtil.getInterceptor(toolExecutionRequest.name());
            if (toolInterceptor != null) {
                toolInterceptor.setText(content.text());
                toolInterceptor.setToolInfo(toolExecutionRequest.arguments());
                toolInterceptor.setId(toolExecutionRequest.id());
                toolInterceptor.setRequestId(requestId);
                return Map.of("tool_interceptor", JSONObject.toJSONString(toolInterceptor), "messages", content, "tool_post_interceptor", "", "agent_response", "");
            }
            return Map.of("messages", content, "tool_interceptor", "", "tool_post_interceptor", "", "agent_response", "");

        }

        throw new IllegalStateException("Unsupported finish reason: " + response.finishReason() );
    }

    /**
     * Applies the action to the given state and returns the result.
     *
     * @param state the state to which the action is applied
     * @return a map containing the agent's outcome
     * @throws IllegalArgumentException if no input is provided in the state
     */
    @Override
    public Map<String,Object> apply( AgentMessageState state )  {
        log.info( "callAgent" );
        var messages = state.messages();

        if( messages.isEmpty() ) {
            throw new IllegalArgumentException("no input provided!");
        }


        if( agent.isStreaming()) {

            if (generator == null) {
                generator = StreamingChatGenerator.<AgentMessageState>builder()
                        .queue(queue)
                        .mapResult( this::mapResult )
                        .startingNode(agentName)
                        .startingState( state )
                        .build();
            }

            agent.execute(messages, generator.handler());

            return Map.of( "_generator", generator);


        }
        else {
            var response = agent.execute(messages);

            return mapResult(response);
        }

    }
}
