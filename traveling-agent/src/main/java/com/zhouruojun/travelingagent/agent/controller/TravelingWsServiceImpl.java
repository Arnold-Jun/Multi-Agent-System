package com.zhouruojun.travelingagent.agent.controller;

import com.alibaba.fastjson.JSONObject;
import com.zhouruojun.travelingagent.agent.core.TravelingControllerCore;
import com.zhouruojun.travelingagent.agent.dto.AgentChatRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;

/**
 * Traveling Agent WebSocket Service 实现类
 * 处理WebSocket消息并转发到TravelingControllerCore
 */
@Service
@Slf4j
public class TravelingWsServiceImpl implements TravelingWsService {

    private static final String PONG = "Pong";

    @Autowired
    private TravelingControllerCore travelingControllerCore;

    @Override
    public void chat(LinkedHashMap<String, Object> payload, String userEmail) {
        chat(parseAgentChatRequestFromPayload(payload), userEmail);
    }

    @Override
    public void chat(AgentChatRequest request, String userEmail) {
        log.info("WebSocket chat request - sessionId: {}, chat: {}, userEmail: {}", 
                request.getSessionId(), request.getChat(), userEmail);
        
        // 设置sessionId（如果没有的话）
        if (request.getSessionId() == null || request.getSessionId().isEmpty()) {
            request.setSessionId(request.getRequestId());
        }
        
        // 调用TravelingControllerCore处理
        travelingControllerCore.processStreamWithWs(request, userEmail);
    }

    @Override
    public void humanInput(LinkedHashMap<String, Object> payload, String userEmail) {
        humanInput(parseAgentChatRequestFromPayload(payload), userEmail);
    }

    @Override
    public void humanInput(AgentChatRequest request, String userEmail) {
        log.info("HumanInput request - sessionId: {}, chat: {}, userEmail: {}",
                request.getSessionId(), request.getChat(), userEmail);
        
        // 调用TravelingControllerCore处理用户输入
        travelingControllerCore.humanInputWithWs(request, userEmail);
    }

    @Override
    public String ping() {
        return PONG;
    }

    /**
     * 从LinkedHashMap解析AgentChatRequest
     */
    private AgentChatRequest parseAgentChatRequestFromPayload(LinkedHashMap<String, Object> payload) {
        try {
            AgentChatRequest agentChatRequest = new AgentChatRequest();
            agentChatRequest.setChat((String) payload.get("chat"));
            agentChatRequest.setRequestId((String) payload.get("requestId"));
            agentChatRequest.setSessionId((String) payload.get("sessionId"));
            
            // 如果没有sessionId，使用requestId
            if (agentChatRequest.getSessionId() == null || agentChatRequest.getSessionId().isEmpty()) {
                agentChatRequest.setSessionId(agentChatRequest.getRequestId());
            }
            
            return agentChatRequest;
        } catch (Exception e) {
            log.error("Failed to parse AgentChatRequest from payload: {}", JSONObject.toJSONString(payload), e);
            throw new RuntimeException("Failed to parse AgentChatRequest", e);
        }
    }
}

