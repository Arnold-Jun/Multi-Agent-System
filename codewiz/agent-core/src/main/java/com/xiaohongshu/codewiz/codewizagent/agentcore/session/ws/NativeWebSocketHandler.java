package com.xiaohongshu.codewiz.codewizagent.agentcore.session.ws;

import com.alibaba.fastjson.JSONObject;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaohongshu.codewiz.a2acore.spec.error.InternalError;
import com.xiaohongshu.codewiz.a2acore.spec.error.InvalidRequestError;
import com.xiaohongshu.codewiz.a2acore.spec.error.JsonRpcError;
import com.xiaohongshu.codewiz.a2acore.spec.message.JsonRpcRequest;
import com.xiaohongshu.codewiz.a2acore.spec.message.JsonRpcResponse;
import com.xiaohongshu.codewiz.codewizagent.agentcore.agent.AgentChatRequest;
import com.xiaohongshu.codewiz.codewizagent.agentcore.auth.AuthServer;
import com.xiaohongshu.codewiz.codewizagent.agentcore.common.ApplicationContextUtil;
import com.xiaohongshu.codewiz.codewizagent.agentcore.session.SessionManager;
import com.xiaohongshu.codewiz.codewizagent.agentcore.session.UserPrincipal;
import com.xiaohongshu.codewiz.codewizagent.agentcore.session.json.JsonRpcMethodRegistry;
import com.xiaohongshu.infra.rpc.ciuser.RpcBaseUserInfo;
import jakarta.annotation.Resource;
import java.io.IOException;
import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.concurrent.CompletableFuture;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PongMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * 对于Agent-core的websocket来说，sessionId为前序的requestId，
 * 而真正对应上Websocket链接的是用户名+工作空间。
 * 全局会将sessionId作为sessionId透传到上下游
 */
@Slf4j
@Component
public class NativeWebSocketHandler extends TextWebSocketHandler {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private JsonRpcMethodRegistry jsonRpcMethodRegistry = null;

    private static final String CONTENT_LENGTH = "Content-Length: ";

    @Resource
    private AuthServer authServer;

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        CompletableFuture.runAsync(() -> processMessage(message.getPayload(), session));
    }

    private void processMessage(String message, WebSocketSession session) {
        try {
            JsonRpcRequest request = parseRequest(extractJsonBody(message));
            JsonRpcError jsonRpcError = validateRequest(request);
            if (jsonRpcError != null) {
                sendError(session, request.getId(), jsonRpcError);
                return;
            }

            if (jsonRpcMethodRegistry == null) {
                jsonRpcMethodRegistry = ApplicationContextUtil.getBean(JsonRpcMethodRegistry.class);
            }

            Object result;
            if (session.getPrincipal() != null && session.getPrincipal() instanceof UserPrincipal) {
                if (request.getParams() instanceof LinkedHashMap map) {
                    if (!map.containsKey("workspace")) {
                        map.put("workspace",
                                ObjectUtils.isEmpty(session.getAttributes().get("workspace")) ?
                                        "" :  session.getAttributes().get("workspace").toString());
                        request.setParams(map);
                    }
                }
                result = jsonRpcMethodRegistry.invokeMethodWithUser(request.getMethod(), request.getParams(), (UserPrincipal) session.getPrincipal());
            } else {
                result = jsonRpcMethodRegistry.invokeMethod(request.getMethod(), request.getParams());
            }

            if (result == null) {
                return;
            }
            if (result instanceof JsonRpcError) {
                sendError(session, request.getId(), (JsonRpcError) result);
                return;
            }
            sendResponse(session, request.getId(), result);
        } catch (Exception e) {
            log.error("处理请求失败", e);
            sendError(session, null, new InternalError(e.getMessage()));
        }
    }

    @Override
    protected void handlePongMessage(WebSocketSession session, PongMessage message) throws Exception {
        session.sendMessage(new TextMessage("pong"));
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        log.info("WebSocket 连接建立: {}", session.getId());
        RpcBaseUserInfo baseUserInfo = (RpcBaseUserInfo)session.getAttributes().get("user");
        String workspace = (String)session.getAttributes().get("workspace");
        SessionManager.getInstance().addSession(baseUserInfo.getEmail(), workspace, session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        log.info("Websocket 断开连接：{}", session.getId());
        RpcBaseUserInfo baseUserInfo = (RpcBaseUserInfo)session.getAttributes().get("user");
        String workspace = (String)session.getAttributes().get("workspace");
        SessionManager.getInstance().removeSession(baseUserInfo.getEmail(), workspace);
    }

    private JsonRpcRequest parseRequest(String message) throws JsonProcessingException {
        return objectMapper.readValue(message, JsonRpcRequest.class);
    }

    private JsonRpcError validateRequest(JsonRpcRequest request) {
        if (!"2.0".equals(request.getJsonrpc())) {
            return new InvalidRequestError();
        }
        return null;
    }

    private void sendResponse(WebSocketSession session, Object id, Object result) {
        try {
            JsonRpcResponse response = new JsonRpcResponse();
            response.setResult(result);
            response.setId((String) id);
            String json = objectMapper.writeValueAsString(response);
            session.sendMessage(new TextMessage(json));
        } catch (Exception e) {
            log.error("发送响应失败", e);
        }
    }

    private void sendError(WebSocketSession session, Object id, JsonRpcError error) {
        try {
            JsonRpcResponse response = new JsonRpcResponse();
            response.setError(error);
            response.setId((String) id);
            String json = objectMapper.writeValueAsString(response);
            session.sendMessage(new TextMessage(json));
        } catch (Exception e) {
            log.error("发送错误响应失败", e);
        }
    }


    // 提取消息中的 JSON 部分（兼容 HTTP 头）
    private String extractJsonBody(String rawMessage) {
        // 使用正则分割头部和体部（兼容不同换行符）
        String[] parts = rawMessage.split("\\r?\\n\\r?\\n", 2);
        return (parts.length > 1) ? parts[1] : rawMessage;
    }

//    private void sendMessage(WebSocketSession session, String message) {
//        try {
//            JsonRpcRequest jsonRpcRequest = new JsonRpcRequest();
//            jsonRpcRequest.setJsonrpc("2.0");
//            jsonRpcRequest.setId(session.getId());
//            jsonRpcRequest.setParams(message);
//            jsonRpcRequest.setMethod("agent/observer");
//            session.sendMessage(new TextMessage(JSONObject.toJSONString(jsonRpcRequest)));
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//    }

    public static void closeSession(WebSocketSession session) {
        try {
            session.close(CloseStatus.NORMAL);
        } catch (Exception e) {
            Principal principal = session.getPrincipal();
            String user = null;
            if (principal != null) {
                user = principal.getName();
            }
            log.error("关闭WebSocket会话异常，sessionId:{}, sessionUser:{}", session.getId(), user, e);
        }

    }

    public static void sendMessage(WebSocketSession session, String message, String method) {
        JsonRpcRequest jsonRpcRequest = new JsonRpcRequest();
        jsonRpcRequest.setId(session.getId());
        jsonRpcRequest.setParams(message);
        jsonRpcRequest.setMethod(method);
        String rpcMessage = JSONObject.toJSONString(jsonRpcRequest);
        try {
            // 强行适配IDE插件的websocket处理端，需要Content-Length头部
            rpcMessage = CONTENT_LENGTH + rpcMessage.length() + "\r\n" + rpcMessage;
            session.sendMessage(new TextMessage(rpcMessage));
            log.info("发生消息:{}", rpcMessage);
        } catch (IOException e) {
            log.error("发送消息失败:{}", rpcMessage, e);
        }
    }
}