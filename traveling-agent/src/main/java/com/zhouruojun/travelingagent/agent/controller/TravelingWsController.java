package com.zhouruojun.travelingagent.agent.controller;

import com.zhouruojun.travelingagent.agent.dto.TravelIntentForm;
import com.alibaba.fastjson.JSONObject;
import com.zhouruojun.travelingagent.agent.dto.AgentChatRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.Headers;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Traveling Agent WebSocket Controller
 * 处理WebSocket消息路由
 */
@Controller
@Slf4j
public class TravelingWsController {

    @Autowired
    private TravelingWsService travelingWsService;

    /**
     * 处理聊天消息
     * 路径: /traveling/chat
     */
    @MessageMapping("/traveling/chat")
    public void chat(@Payload Object payload, @Headers Map<String, Object> headers) {
        String userEmail = extractUserEmail(headers);
        String payloadStr = parsePayload(payload);
        log.info("WebSocket chat - userEmail: {}, payload: {}", userEmail, payloadStr);
        
        try {
            JSONObject jsonObject = JSONObject.parseObject(payloadStr);
            AgentChatRequest agentChatRequest = jsonObject.getObject("request", AgentChatRequest.class);
            travelingWsService.chat(agentChatRequest, userEmail);
        } catch (Exception e) {
            log.error("Failed to parse chat payload", e);
            throw new RuntimeException("Failed to parse chat payload", e);
        }
    }

    /**
     * 处理用户输入
     * 路径: /traveling/human/input
     */
    @MessageMapping("/traveling/human/input")
    public void humanInput(@Payload Object payload, @Headers Map<String, Object> headers) {
        String userEmail = extractUserEmail(headers);
        String payloadStr = parsePayload(payload);

        try {
            JSONObject jsonObject = JSONObject.parseObject(payloadStr);
            AgentChatRequest agentChatRequest = jsonObject.getObject("request", AgentChatRequest.class);
            travelingWsService.humanInput(agentChatRequest, userEmail);
        } catch (Exception e) {
            log.error("Failed to parse humanInput payload", e);
            throw new RuntimeException("Failed to parse humanInput payload", e);
        }
    }

    /**
     * 处理表单提交
     * 路径: /traveling/form/submit
     */
    @MessageMapping("/traveling/form/submit")
    public void submitForm(@Payload Object payload, @Headers Map<String, Object> headers) {
        String userEmail = extractUserEmail(headers);
        String payloadStr = parsePayload(payload);
        log.info("WebSocket form submit - userEmail: {}, payload: {}", userEmail, payloadStr);

        try {
            JSONObject jsonObject = JSONObject.parseObject(payloadStr);
            TravelIntentForm form = jsonObject.getObject("form", TravelIntentForm.class);
            travelingWsService.submitForm(form, userEmail);
        } catch (Exception e) {
            log.error("Failed to parse form submit payload", e);
            throw new RuntimeException("Failed to parse form submit payload", e);
        }
    }

    /**
     * Ping测试
     * 路径: /traveling/ping
     */
    @MessageMapping("/traveling/ping")
    public String ping() {
        return travelingWsService.ping();
    }

    /**
     * 解析payload
     */
    private String parsePayload(Object payload) {
        if (payload instanceof byte[]) {
            return new String((byte[]) payload, StandardCharsets.UTF_8);
        } else {
            return payload.toString();
        }
    }

    /**
     * 从headers中提取用户邮箱
     */
    private String extractUserEmail(Map<String, Object> headers) {
        // 尝试从headers中获取用户信息
        Object userObj = headers.get("simpUser");
        if (userObj != null) {
            return userObj.toString();
        }
        
        // 默认用户
        return "anonymous@traveling.com";
    }
}

