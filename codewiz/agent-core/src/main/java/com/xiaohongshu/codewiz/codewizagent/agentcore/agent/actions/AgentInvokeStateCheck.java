package com.xiaohongshu.codewiz.codewizagent.agentcore.agent.actions;

import com.alibaba.fastjson.JSONObject;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaohongshu.codewiz.a2acore.spec.DataPart;
import com.xiaohongshu.codewiz.a2acore.spec.Part;
import com.xiaohongshu.codewiz.a2acore.spec.TaskArtifactUpdateEvent;
import com.xiaohongshu.codewiz.a2acore.spec.TaskSendParams;
import com.xiaohongshu.codewiz.a2acore.spec.TaskState;
import com.xiaohongshu.codewiz.a2acore.spec.TaskStatus;
import com.xiaohongshu.codewiz.a2acore.spec.TaskStatusUpdateEvent;
import com.xiaohongshu.codewiz.a2acore.spec.TextPart;
import com.xiaohongshu.codewiz.codewizagent.agentcore.a2a.A2aClientManager;
import com.xiaohongshu.codewiz.codewizagent.agentcore.a2a.A2aTaskManager;
import com.xiaohongshu.codewiz.codewizagent.agentcore.a2a.A2aTaskUpdate;
import com.xiaohongshu.codewiz.codewizagent.agentcore.agent.state.AgentMessageState;
import com.xiaohongshu.codewiz.codewizagent.agentcore.mcp.local.LocalAgentReplyMessage;
import com.xiaohongshu.codewiz.codewizagent.agentcore.mcp.local.LocalExecuteToolMessageResponse;
import com.xiaohongshu.codewiz.codewizagent.agentcore.mcp.local.LocalToolMessageResponse;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;
import org.bsc.langgraph4j.langchain4j.generators.StreamingChatGenerator;

/**
 * <p>
 *
 * </p>
 *
 * @author 瑞诺
 * create on 2025/5/14 15:16
 */
@Slf4j
public class AgentInvokeStateCheck implements NodeAction<AgentMessageState> {

    private A2aClientManager a2aClientManager;

    private ObjectMapper objectMapper;

    public AgentInvokeStateCheck(A2aClientManager a2aClientManager) {
        this.a2aClientManager = a2aClientManager;
        objectMapper = new ObjectMapper();
    }

    /**
     * 判断当前流程是什么agent以及什么进展。
     * 通过sessionId的最近Task来判断Agent的状态。
     * Agent与Agent之间目前为串行。
     * @param state
     * @return
     * @throws Exception
     */
    @Override
    public Map<String, Object> apply(AgentMessageState state) throws Exception {
        log.info( "AgentInvokeStateCheck" );
        var messages = state.messages();

        if( messages.isEmpty() ) {
            throw new IllegalArgumentException("no input provided!");
        }

        // 先判断当前流程的状态
        if (state.sessionId().isEmpty()) {
            throw new IllegalArgumentException("sessionId is empty");
        }

        ChatMessage chatMessage = state.lastMessage().get();
        if (chatMessage instanceof UserMessage userMessage) {
            // 用户输入，场景为对工具的确认、重试。对流程的取消，中断
            if (state.method().isEmpty()) {
                throw new IllegalArgumentException("method is empty");
            }
            // 用户输入，直接透传给下游，下游自行决定根据当前入参进行续订或状态变更。
            return Map.of("next", "subscribe",
                    "method", state.method().get(),
                    "nextAgent", state.currentAgent().get(),
                    "messages", userMessage.singleText()
            );
        } else if (chatMessage instanceof AiMessage aiMessage) {
            // 其他的agent事件，事件用于更新数据。以及交互。
            String text = aiMessage.text();
//            A2aTaskUpdate a2aTaskUpdate = JSONObject.parseObject(text, A2aTaskUpdate.class);
            A2aTaskUpdate a2aTaskUpdate = objectMapper.readValue(text, A2aTaskUpdate.class);
            if ("status".equals(a2aTaskUpdate.getUpdateType())) {
                TaskStatusUpdateEvent taskStatusUpdateEvent = (TaskStatusUpdateEvent) a2aTaskUpdate.getUpdateEvent();
                TaskState taskState = taskStatusUpdateEvent.getStatus().getState();
                if (TaskState.CANCELED.equals(taskState)
                        || TaskState.UNKNOWN.equals(taskState)
                        || TaskState.FAILED.equals(taskState)
                        || TaskState.COMPLETED.equals(taskState)
                ) {
                    return Map.of("next", "FINISH");
                }
                return Map.of("next", "continue");
            } else {
                TaskArtifactUpdateEvent artifactUpdateEvent = (TaskArtifactUpdateEvent) a2aTaskUpdate.getUpdateEvent();
                log.info("task update, artifactUpdateEvent:{}", JSONObject.toJSONString(artifactUpdateEvent));
                return Map.of("next", "continue");
            }
        } else if (chatMessage instanceof ToolExecutionResultMessage toolExecutionResultMessage) {
            // 本地IDE工具调用请求执行
            return Map.of("next", "subscribe",
                    "method", state.method().get(),
                    "nextAgent", state.currentAgent().get(),
                    "messages", toolExecutionResultMessage
            );
        }
        throw new IllegalArgumentException("unknown chat message");
    }

}
