package com.xiaohongshu.codewiz.codewizagent.agentcore.agent.controller;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.xiaohongshu.codewiz.codewizagent.agentcore.agent.AgentChatRequest;
import com.xiaohongshu.codewiz.codewizagent.agentcore.mcp.ToolSpecificationHelper;
import com.xiaohongshu.codewiz.codewizagent.agentcore.mcp.local.LocalAgentReplyMessage;
import com.xiaohongshu.codewiz.codewizagent.agentcore.mcp.local.LocalExecuteToolMessageResponse;
import com.xiaohongshu.codewiz.codewizagent.agentcore.mcp.local.LocalToolMessageResponse;
import com.xiaohongshu.codewiz.codewizagent.agentcore.mcp.local.ToolInterceptorRequest;
import com.xiaohongshu.codewiz.codewizagent.agentcore.session.UserPrincipal;
import dev.langchain4j.agent.tool.ToolSpecification;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.Headers;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

/**
 * <p>
 *
 * </p>
 *
 * @author 瑞诺
 * create on 2025/3/29 04:46
 */
@Controller
@Slf4j
public class AgentWsController {

    @Resource
    private AgentWsService agentWsService;

    @Resource
    private ObjectMapper objectMapper;

    @MessageMapping("/chat")
    public void chat(@Payload Object payload, @Headers Map<String, Object> headers, UserPrincipal user) {
        if (payload instanceof String) {
            String payloadStr = parsePayload(payload);
            log.info("chat payload:{}", payloadStr);
            AgentChatRequest agentChatRequest;
            List<ToolSpecification> userTools;
            try {
                JSONObject jsonObject = JSONObject.parseObject(payloadStr);
                agentChatRequest = jsonObject.getObject("request", AgentChatRequest.class);
                String toolsStr = jsonObject.getString("tools");
                ArrayNode arrayNode = (ArrayNode) objectMapper.readTree(toolsStr);
                userTools = ToolSpecificationHelper.toolSpecificationListFromMcpResponse(arrayNode);
                agentChatRequest.setUserTools(userTools);
            } catch (Exception e) {
                log.error("parse payload error", e);
                throw new RuntimeException("parse payload error");
            }
            agentWsService.chat(agentChatRequest, user.getName());
        }
    }

    @MessageMapping("/action")
    public void action(@Payload Object payload, @Headers Map<String, Object> headers, UserPrincipal user) {
        String payloadStr = parsePayload(payload);
        log.info("action payload:{}", payloadStr);
        JSONObject payloadJSON = JSONObject.parseObject(payloadStr).getJSONObject("messages");
        LocalToolMessageResponse localToolMessageResponse = new LocalToolMessageResponse();
        localToolMessageResponse.setRequestId(payloadJSON.getString("requestId"));
        JSONArray toolsJSON = payloadJSON.getJSONArray("tools");
        if (toolsJSON == null) {
            log.error("tools is null, payload:{}", payload);
            return;
        }
        List<LocalExecuteToolMessageResponse> tools = new ArrayList<>();
        for (Object tool : toolsJSON) {
            JSONObject toolJSON = (JSONObject) tool;
            LocalExecuteToolMessageResponse localToolMessage = new LocalExecuteToolMessageResponse();
            localToolMessage.setId(toolJSON.getString("id"));
            localToolMessage.setToolName(toolJSON.getString("toolName"));
            localToolMessage.setText(toolJSON.getString("text"));
            tools.add(localToolMessage);
        }
        localToolMessageResponse.setTools(tools);
        LocalAgentReplyMessage agentReplyMessage = new LocalAgentReplyMessage();
        agentReplyMessage.setResponse(localToolMessageResponse);
        agentWsService.action(agentReplyMessage, user.getName());
    }

    @MessageMapping("/human/confirm")
    public void humanConfirm(@Payload Object payload, @Headers Map<String, Object> headers, UserPrincipal user) {
        String payloadStr = parsePayload(payload);
        log.info("humanConfirm payload:{}", payloadStr);
        ToolInterceptorRequest toolInterceptorRequest = JSONObject.parseObject(payloadStr).getObject("request", ToolInterceptorRequest.class);
        agentWsService.humanConfirm(toolInterceptorRequest, user.getName());
    }

    @MessageMapping("/human/replay")
    public void humanReplay(@Payload Object payload, @Headers Map<String, Object> headers, UserPrincipal user) {
        String payloadStr = parsePayload(payload);
        log.info("humanReplay payload:{}", payloadStr);
        ToolInterceptorRequest toolInterceptorRequest = JSONObject.parseObject(payloadStr).getObject("request", ToolInterceptorRequest.class);
        agentWsService.humanReplay(toolInterceptorRequest, user.getName());
    }

    @MessageMapping("/human/input")
    public void humanInput(@Payload Object payload, @Headers Map<String, Object> headers, UserPrincipal user) {
        String payloadStr = parsePayload(payload);
        log.info("humanInput payload:{}", payloadStr);
        JSONObject jsonObject = JSONObject.parseObject(payloadStr);
        AgentChatRequest agentChatRequest = jsonObject.getObject("request", AgentChatRequest.class);
        agentWsService.humanInput(agentChatRequest, user.getName());
    }

    public String parsePayload(Object payload) {
        String payloadStr;
        if (payload instanceof byte[]) {
            // 如果payload是byte[]类型，转换为字符串
            // 注意：这里假设byte[]是UTF-8编码的字符串
            // 如果不是UTF-8编码，可能会导致乱码
            // 你可以根据实际情况进行编码转换
            // 例如：String payloadStr = new String((byte[]) payload, StandardCharsets.UTF_8);
            payloadStr = new String((byte[]) payload, StandardCharsets.UTF_8);
        } else {
            payloadStr = payload.toString();
        }
        return payloadStr;
    }

}
