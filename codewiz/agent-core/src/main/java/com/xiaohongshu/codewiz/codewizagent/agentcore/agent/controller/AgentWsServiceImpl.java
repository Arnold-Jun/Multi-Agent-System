package com.xiaohongshu.codewiz.codewizagent.agentcore.agent.controller;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.xiaohongshu.codewiz.codewizagent.agentcore.agent.AgentChatRequest;
import com.xiaohongshu.codewiz.codewizagent.agentcore.agent.AgentOperateRequest;
import com.xiaohongshu.codewiz.codewizagent.agentcore.agent.controller.AgentWsService;
import com.xiaohongshu.codewiz.codewizagent.agentcore.agent.core.AgentControllerCore;
import com.xiaohongshu.codewiz.codewizagent.agentcore.mcp.ToolInterceptorEnum;
import com.xiaohongshu.codewiz.codewizagent.agentcore.mcp.ToolSpecificationHelper;
import com.xiaohongshu.codewiz.codewizagent.agentcore.mcp.local.LocalAgentReplyMessage;
import com.xiaohongshu.codewiz.codewizagent.agentcore.mcp.local.LocalToolMessageRequest;
import com.xiaohongshu.codewiz.codewizagent.agentcore.mcp.local.LocalToolMessageResponse;
import com.xiaohongshu.codewiz.codewizagent.agentcore.mcp.local.ToolInterceptorRequest;
import com.xiaohongshu.codewiz.codewizagent.agentcore.session.SessionManager;
import dev.langchain4j.agent.tool.ToolSpecification;
import java.util.LinkedHashMap;
import java.util.List;
import javax.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * <p>
 *
 * </p>
 *
 * @author 瑞诺
 * create on 2025/3/29 04:46
 */
@Service
@Slf4j
public class AgentWsServiceImpl implements AgentWsService {

    private static final String PONE = "Pong";

    @Resource
    private AgentControllerCore agentControllerCore;

    @Resource
    private ObjectMapper objectMapper;
//    @Resource
//    private SessionManager sessionManager;

    @Override
    public void chat(LinkedHashMap payload, String userEmail) {
        chat(parseAgentChatRequestFromPayload(payload), userEmail);
    }

    @Override
    public void chat(AgentChatRequest agentChatRequest, String userEmail) {
        // 发起
        agentChatRequest.setSessionId(agentChatRequest.getRequestId());
        SessionManager.getInstance().addSessionRequest(userEmail, agentChatRequest.getWorkspace(), agentChatRequest.getSessionId());
        agentControllerCore.processStreamWithWs(agentChatRequest, agentChatRequest.getUserTools(), userEmail);
    }

    @Override
    public void action(LinkedHashMap payload, String userEmail) {
//        LocalAgentReplyMessage localAgentReplyMessage = new LocalAgentReplyMessage();
//        localAgentReplyMessage.setRequest((LocalToolMessageRequest) payload.getOrDefault("request", new LocalToolMessageRequest()));
//        localAgentReplyMessage.setResponse((LocalToolMessageResponse) payload.getOrDefault("response", new LocalToolMessageResponse()));
        try {
            LocalAgentReplyMessage localAgentReplyMessage =
                    objectMapper.readValue(objectMapper.writeValueAsString(payload), LocalAgentReplyMessage.class);
            action(localAgentReplyMessage, userEmail);
        } catch (Exception e) {
            log.error("action:{}, error:{}", JSONObject.toJSONString(payload), e.getMessage(), e);
        }
    }

    @Override
    public void action(LocalAgentReplyMessage localAgentReplyMessage, String userEmail) {
        agentControllerCore.resumeStreamWithTools(localAgentReplyMessage, userEmail);
    }

    @Override
    public void humanConfirm(LinkedHashMap payload, String userEmail) {
//        ToolInterceptorRequest toolInterceptorRequest = new ToolInterceptorRequest();
//        toolInterceptorRequest.setName((String) payload.getOrDefault("name", ""));
//        toolInterceptorRequest.setRequestId((String) payload.getOrDefault("requestId", ""));
//        toolInterceptorRequest.setToolInfo((String) payload.getOrDefault("toolInfo", "'"));
//        toolInterceptorRequest.setText((String) payload.getOrDefault("text", ""));
//        toolInterceptorRequest.setUserMessage((String) payload.getOrDefault("userMessage", ""));
//        toolInterceptorRequest.setType((ToolInterceptorEnum) payload.getOrDefault("type", ToolInterceptorEnum.CONFIRM));
        try {
            ToolInterceptorRequest toolInterceptorRequest =
                    objectMapper.readValue(objectMapper.writeValueAsString(payload), ToolInterceptorRequest.class);
            humanConfirm(toolInterceptorRequest, "human_confirm");
        } catch (Exception e) {
            log.error("humanConfirm:{}, error:{}", JSONObject.toJSONString(payload), e.getMessage(), e);
        }
    }

    @Override
    public void humanConfirm(ToolInterceptorRequest toolInterceptorRequest, String method) {
//        agentControllerCore.humanConfirm(toolInterceptorRequest)
        agentControllerCore.humanToolsStream(toolInterceptorRequest, method);
    }

    @Override
    public void humanReplay(LinkedHashMap payload, String userEmail) {
        try {
            ToolInterceptorRequest toolInterceptorRequest =
                    objectMapper.readValue(objectMapper.writeValueAsString(payload), ToolInterceptorRequest.class);
            humanReplay(toolInterceptorRequest, "human_replay");
        } catch (Exception e) {
            log.error("humanReplay:{}, error:{}", JSONObject.toJSONString(payload), e.getMessage(), e);
        }
    }

    @Override
    public void humanReplay(ToolInterceptorRequest toolInterceptorRequest, String method) {
//        agentControllerCore.humanReplay(toolInterceptorRequest);
        agentControllerCore.humanToolsStream(toolInterceptorRequest, method);
    }

    @Override
    public void humanInput(LinkedHashMap payload, String userEmail) {
        humanInput(parseAgentChatRequestFromPayload(payload), userEmail);
    }

    @Override
    public void humanInput(AgentChatRequest request, String userEmail) {
        agentControllerCore.humanInput(request);
    }

    @Override
    public String ping() {
        return PONE;
    }

    private AgentChatRequest parseAgentChatRequestFromPayload(LinkedHashMap payload) {
        AgentChatRequest agentChatRequest = new AgentChatRequest();
        agentChatRequest.setChat((String)payload.get("chat"));
        agentChatRequest.setRequestId((String)payload.get("requestId"));
        agentChatRequest.setWorkspace((String)payload.get("workspace"));
        Object operatesMap = payload.get("operates");
        if (operatesMap != null) {
            agentChatRequest.setOperates(JSONArray.parseArray(JSONArray.toJSONString(operatesMap), AgentOperateRequest.class));
        }
        Object userToolsMap = payload.get("userTools");
        if (userToolsMap != null) {
            try {
                ArrayNode arrayNode = (ArrayNode) objectMapper.readTree(JSONObject.toJSONString(userToolsMap));
                List<ToolSpecification> userTools = ToolSpecificationHelper.toolSpecificationListFromMcpResponse(arrayNode);
                agentChatRequest.setUserTools(userTools);
            } catch (Exception e) {
                log.error("parse userTools error, userTools:{}", userToolsMap, e);
                throw new RuntimeException("parse userTools error", e);
            }
        }
        return agentChatRequest;
    }
}
