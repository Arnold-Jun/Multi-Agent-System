package com.zhouruojun.agentcore.agent.actions;

import com.alibaba.fastjson.JSONObject;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhouruojun.agentcore.agent.state.AgentMessageState;
import com.zhouruojun.agentcore.a2a.A2aClientManager;
import com.zhouruojun.agentcore.a2a.A2aTaskUpdate;
import com.zhouruojun.a2acore.spec.TaskStatusUpdateEvent;
import com.zhouruojun.a2acore.spec.TaskArtifactUpdateEvent;
import com.zhouruojun.a2acore.spec.TaskState;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;

import java.util.Map;
// import java.util.Optional; // 未使用

/**
 * Agent调用状态检查节点
 * 检查智能体调用的状态并决定下一步操作
 * 实现codewiz的interrupt+resume机制
 */
@Slf4j
public class AgentInvokeStateCheck implements NodeAction<AgentMessageState> {

    @SuppressWarnings("unused") // 暂时未使用，保留以备将来扩展
    private final A2aClientManager a2aClientManager;
    private final ObjectMapper objectMapper;

    public AgentInvokeStateCheck(A2aClientManager a2aClientManager) {
        this.a2aClientManager = a2aClientManager;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public Map<String, Object> apply(AgentMessageState state) throws Exception {
        log.info("=== AgentInvokeStateCheck executing ===");
        log.info("AgentInvokeStateCheck state data: {}", JSONObject.toJSONString(state.data()));
        
        var messages = state.messages();
        if (messages.isEmpty()) {
            throw new IllegalArgumentException("no input provided!");
        }

        // 检查sessionId
        if (state.sessionId().isEmpty()) {
            throw new IllegalArgumentException("sessionId is empty");
        }

        ChatMessage chatMessage = state.lastMessage().get();
        
        if (chatMessage instanceof UserMessage userMessage) {
            // 用户输入，场景为对工具的确认、重试。对流程的取消，中断
            if (state.method().isEmpty()) {
                throw new IllegalArgumentException("method is empty");
            }
            // 用户输入，直接回到supervisor让用户重新开始对话，避免重复调用
            log.info("User input received during task execution: '{}', returning to supervisor", userMessage.singleText());
            Map<String, Object> result = Map.of("next", "FINISH",
                    "agent_response", "用户输入已接收，请重新开始对话"
            );
            log.info("AgentInvokeStateCheck returning (user input): {}", JSONObject.toJSONString(result));
            return result;
            
        } else if (chatMessage instanceof AiMessage aiMessage) {
            // 来自其他Agent的事件响应
            String text = aiMessage.text();
            A2aTaskUpdate a2aTaskUpdate = objectMapper.readValue(text, A2aTaskUpdate.class);
            
            if ("status".equals(a2aTaskUpdate.getUpdateType())) {
                TaskStatusUpdateEvent taskStatusUpdateEvent = (TaskStatusUpdateEvent) a2aTaskUpdate.getUpdateEvent();
                TaskState taskState = taskStatusUpdateEvent.getStatus().getState();
                
                if (TaskState.CANCELED.equals(taskState)
                        || TaskState.UNKNOWN.equals(taskState)
                        || TaskState.FAILED.equals(taskState)
                        || TaskState.COMPLETED.equals(taskState)
                ) {
                    log.info("Task completed with state: {}, finishing", taskState);
                    Map<String, Object> result = Map.of("next", "FINISH");
                    log.info("AgentInvokeStateCheck returning (task completed): {}", JSONObject.toJSONString(result));
                    return result;
                }
                
                log.info("Task still working, continuing to wait");
                Map<String, Object> result = Map.of("next", "continue");
                log.info("AgentInvokeStateCheck returning (task working): {}", JSONObject.toJSONString(result));
                return result;
                
            } else {
                // Artifact更新
                TaskArtifactUpdateEvent artifactUpdateEvent = (TaskArtifactUpdateEvent) a2aTaskUpdate.getUpdateEvent();
                log.info("task update, artifactUpdateEvent:{}", JSONObject.toJSONString(artifactUpdateEvent));
                Map<String, Object> result = Map.of("next", "continue");
                log.info("AgentInvokeStateCheck returning (artifact update): {}", JSONObject.toJSONString(result));
                return result;
            }
            
        } else if (chatMessage instanceof ToolExecutionResultMessage toolExecutionResultMessage) {
            // 本地IDE工具调用请求执行，直接完成当前任务
            log.info("Tool execution result received: '{}', completing task", toolExecutionResultMessage.text());
            Map<String, Object> result = Map.of("next", "FINISH",
                    "agent_response", "工具执行完成"
            );
            log.info("AgentInvokeStateCheck returning (tool execution): {}", JSONObject.toJSONString(result));
            return result;
        }
        
        throw new IllegalArgumentException("unknown chat message type: " + chatMessage.getClass().getSimpleName());
    }
}
