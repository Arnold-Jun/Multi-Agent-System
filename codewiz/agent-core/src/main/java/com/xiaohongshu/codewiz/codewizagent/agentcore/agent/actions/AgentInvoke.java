package com.xiaohongshu.codewiz.codewizagent.agentcore.agent.actions;

import com.alibaba.fastjson.JSONObject;
import com.xiaohongshu.codewiz.a2acore.client.A2AClient;
import com.xiaohongshu.codewiz.a2acore.spec.AgentCard;
import com.xiaohongshu.codewiz.a2acore.spec.DataPart;
import com.xiaohongshu.codewiz.a2acore.spec.Message;
import com.xiaohongshu.codewiz.a2acore.spec.Part;
import com.xiaohongshu.codewiz.a2acore.spec.Role;
import com.xiaohongshu.codewiz.a2acore.spec.TaskSendParams;
import com.xiaohongshu.codewiz.a2acore.spec.TextPart;
import com.xiaohongshu.codewiz.a2acore.spec.message.SendTaskStreamingResponse;
import com.xiaohongshu.codewiz.a2acore.spec.message.util.Uuid;
import com.xiaohongshu.codewiz.codewizagent.agentcore.a2a.A2aClientManager;
import com.xiaohongshu.codewiz.codewizagent.agentcore.a2a.A2aTaskManager;
import com.xiaohongshu.codewiz.codewizagent.agentcore.agent.state.AgentMessageState;
import com.xiaohongshu.codewiz.codewizagent.agentcore.mcp.local.LocalAgentReplyMessage;
import com.xiaohongshu.codewiz.codewizagent.agentcore.mcp.local.LocalExecuteToolMessageResponse;
import com.xiaohongshu.codewiz.codewizagent.agentcore.mcp.local.LocalToolMessageResponse;
import com.xiaohongshu.codewiz.codewizagent.agentcore.mcp.local.ToolInterceptorRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElementHelper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;
import org.bsc.langgraph4j.langchain4j.generators.StreamingChatGenerator;
import reactor.core.publisher.Flux;

/**
 * <p>
 *
 * </p>
 *
 * @author 瑞诺
 * create on 2025/5/14 15:16
 */
@Slf4j
public class AgentInvoke implements NodeAction<AgentMessageState> {

    private A2aClientManager a2aClientManager;
    private List<JSONObject> JSONTools;

    public AgentInvoke(A2aClientManager a2aClientManager, List<ToolSpecification> toolSpecifications) {
        this.a2aClientManager = a2aClientManager;
        this.JSONTools = toolSpecifications.stream().map(tool -> {
            JSONObject messageTool = new JSONObject();
            messageTool.put("name", tool.name());
            messageTool.put("description", tool.description());
            JsonObjectSchema parameters = tool.parameters();
            Map<String, Object> parametersMap = JsonSchemaElementHelper.toMap(parameters);
            messageTool.put("parameters", parametersMap);
            return messageTool;
        }).collect(Collectors.toList());
    }

    @Override
    public Map<String, Object> apply(AgentMessageState state) throws Exception {
        log.info( "AgentInvoke" );
        var messages = state.messages();

        if( messages.isEmpty() ) {
            throw new IllegalArgumentException("no input provided!");
        }
        if (state.lastMessage().isEmpty()) {
            throw new IllegalArgumentException("no last message provided!");
        }
        Map<String, Object> data = state.data();
        Optional<String> nextAgent = state.nextAgent();
        Optional<String> username = state.username();
        Optional<String> skill = state.skill();
        Optional<String> methodOpt = state.method();

        if (nextAgent.isEmpty()) {
            log.error("nextAgent is null, data:{}", JSONObject.toJSONString(data));
            return Map.of( "next", "userInput", "agent_response", "nextAgent值为空");
        }
        if (username.isEmpty()) {
            log.error("username is null, data:{}", JSONObject.toJSONString(data));
            return Map.of( "next", "userInput", "agent_response", "username值为空");
        }
        String userEmail = (username.get()).contains("@xiaohongshu") ? (username.get()) : username.get() + "@xiaohongshu.com";

        A2AClient a2aClient = a2aClientManager.getA2aClient(nextAgent.get());
        AgentCard agentCard = a2aClient.getAgentCard();

        TaskSendParams taskSendParams;
        if (state.sessionId().isPresent()) {
            taskSendParams = A2aTaskManager.getInstance().lastTaskParams(state.sessionId().get());
        } else {
            taskSendParams = null;
        }

        if (taskSendParams == null) {
            String taskId = Uuid.uuid4hex();
            String method = "chat";
            Part part = parsePartFromChatMessageWithMethod(state, taskId, method);
            List<String> skillStr = skill.map(List::of).orElseGet(List::of);
            Message taskMessage = Message.builder()
                    .role(Role.USER)
                    .parts(List.of(part))
                    .metadata(Map.of())
                    .build();
            taskSendParams = TaskSendParams.builder()
                    .id(taskId)
                    .sessionId(state.sessionId().get())
                    .message(taskMessage)
                    .acceptedOutputModes(getAgentAcceptedOutputModes(agentCard))
                    .pushNotification(a2aClient.getPushNotificationConfig())
                    .metadata(Map.of(
                            "userEmail", userEmail,
                            "userTools", JSONObject.toJSONString(JSONTools),
                            "skill", skillStr,
                            "method", method
                            ))
                    .historyLength(1)
                    .build();
        } else {
            Map<String, Object> metadata = taskSendParams.getMetadata();
            String thisMethod = methodOpt.orElseGet(() -> (String) metadata.get("method"));
            Map<String, Object> copyMap = new HashMap<>(metadata);
            copyMap.put("method", thisMethod);
            taskSendParams.setMetadata(copyMap);

            Part part = parsePartFromChatMessageWithMethod(state, taskSendParams.getId(), thisMethod);
            ArrayList<Part> copyParts = new ArrayList<>(taskSendParams.getMessage().getParts());
            copyParts.add(part);
            taskSendParams.getMessage().setParts(copyParts);
        }

        a2aClientManager.sendTaskSubscribe(nextAgent.get(), taskSendParams);
        log.info("sendTaskSubscribe nextAgent:{}, taskSendParams:{}", nextAgent.get(), JSONObject.toJSONString(taskSendParams));
        A2aTaskManager.getInstance().saveTaskParams(taskSendParams);
        return Map.of("next", "agentInvokeStateCheck", "nextAgent", "", "currentAgent", nextAgent.get());
    }
    
    private Part parsePartFromChatMessageWithMethod(AgentMessageState state, String taskId, String method) {
        ChatMessage chatMessage = state.lastMessage().get();
        if (chatMessage instanceof UserMessage userMessage) {
            if ("user_confirm".equals(method) || "user_replay".equals(method)) {
                DataPart dataPart = new DataPart();
                Map<String, Object> data = new ConcurrentHashMap<>();
                ToolInterceptorRequest toolInterceptorRequest =
                        JSONObject.parseObject(userMessage.singleText(), ToolInterceptorRequest.class);
                data.put("data", toolInterceptorRequest);
                dataPart.setData(data);
                return dataPart;
            }
            return new TextPart(userMessage.singleText());
        } else if (chatMessage instanceof AiMessage aiMessage) {
            return new TextPart(aiMessage.text());
        } else if (chatMessage instanceof ToolExecutionResultMessage toolExecutionResultMessage) {
            LocalExecuteToolMessageResponse localExecuteToolMessageResponse = new LocalExecuteToolMessageResponse();
            localExecuteToolMessageResponse.setId(toolExecutionResultMessage.id());
            localExecuteToolMessageResponse.setToolName(toolExecutionResultMessage.toolName());
            localExecuteToolMessageResponse.setText(toolExecutionResultMessage.text());

            LocalToolMessageResponse localToolMessageResponse = new LocalToolMessageResponse();
            localToolMessageResponse.setRequestId(taskId);
            localToolMessageResponse.setTools(List.of(localExecuteToolMessageResponse));
            LocalAgentReplyMessage localAgentReplyMessage = new LocalAgentReplyMessage();
            localAgentReplyMessage.setResponse(localToolMessageResponse);
            DataPart dataPart = new DataPart();
            Map<String, Object> data = new ConcurrentHashMap<>();
            data.put("data", localAgentReplyMessage);
            dataPart.setData(data);
            return dataPart;
        }
        throw new IllegalArgumentException("unknown chat message");
    }
    

    public List<String> getAgentAcceptedOutputModes(AgentCard agentCard) {
        List<String> systemSupportModes = a2aClientManager.getSystemSupportModes();
        return agentCard.getDefaultOutputModes().stream().filter(systemSupportModes::contains).collect(Collectors.toList());
    }
}
