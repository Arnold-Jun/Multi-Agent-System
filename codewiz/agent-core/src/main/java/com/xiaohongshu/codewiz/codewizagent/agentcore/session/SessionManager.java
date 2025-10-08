package com.xiaohongshu.codewiz.codewizagent.agentcore.session;

import com.alibaba.fastjson.JSON;
import com.xiaohongshu.codewiz.codewizagent.agentcore.session.ws.NativeWebSocketHandler;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.socket.WebSocketSession;

/**
 * <p>
 *
 * </p>
 *
 * @author 瑞诺
 * create on 2025/4/2 11:30
 */
@Slf4j
//@Component
public class SessionManager {

    private static final SessionManager INSTANCE = new SessionManager();

    // 用户维度：用户名称 → 工作空间ID → 会话
    private Map<String, ConcurrentHashMap<String, WebSocketSession>> userSessions;

    // 工作空间维度：工作空间ID → 用户名称 → 会话
    private Map<String, ConcurrentHashMap<String, WebSocketSession>> workspaceSessions;

    // 请求维度：请求ID → 用户名称 + __ + 工作空间ID
    // 当前为agent-core，agent-core的请求ID是sessionId
    private Map<String, String> sessionToUserWorkspaceMap;

    public static final String USER_WORKSPACE_SPLIT = "__";

    private SessionManager() {
        userSessions = new ConcurrentHashMap<>();
        workspaceSessions = new ConcurrentHashMap<>();
        sessionToUserWorkspaceMap = new ConcurrentHashMap<>();
    }

    public static SessionManager getInstance() {
        return INSTANCE;
    }


    public void addSession(String userName, String workspacePath, WebSocketSession session) {
        // 确保线程安全操作
        synchronized (this) {
            // 移除旧会话（如果存在）
            removeSession(userName, workspacePath);
            // 注册到用户维度
            userSessions.computeIfAbsent(userName, k -> new ConcurrentHashMap<>())
                    .put(workspacePath, session);
            // 注册到工作空间维度
            workspaceSessions.computeIfAbsent(workspacePath, k -> new ConcurrentHashMap<>())
                    .put(userName, session);
        }
    }

    public void removeSession(String userName, String workspacePath) {
        // 从用户维度移除
        Optional.ofNullable(userSessions.get(userName)).ifPresent(userMap -> {
            WebSocketSession session = userMap.remove(workspacePath);
            if (userMap.isEmpty()) userSessions.remove(userName);
            NativeWebSocketHandler.closeSession(session); // 关闭物理连接
        });
        // 从工作空间维度移除
        Optional.ofNullable(workspaceSessions.get(workspacePath)).ifPresent(wsMap -> {
            wsMap.remove(userName);
            if (wsMap.isEmpty()) workspaceSessions.remove(workspacePath);
        });
    }

    public void sendToUser(String userName, String message, String method) {
        Optional.ofNullable(userSessions.get(userName)).ifPresent(userMap ->
                userMap.values().forEach(session -> NativeWebSocketHandler.sendMessage(session, message, method))
        );
    }

    public void sendToWorkspace(String userName, String workspacePath, String message, String method) {
        Optional.ofNullable(workspaceSessions.get(workspacePath)).ifPresent(wsMap ->
                {
                    Optional.ofNullable(wsMap.get(userName))
                            .ifPresentOrElse(
                                    session -> NativeWebSocketHandler.sendMessage(session, message, method),
                                    () -> log.error("session is null, userName:{}. workspaceId:{}", userName, workspacePath)
                            );
                }
        );
    }

    public void sendWithSessionId(String sessionId, String messageStr, String method) {
        Optional.ofNullable(sessionToUserWorkspaceMap.get(sessionId))
                .ifPresent(userWorkspace -> {
                    String[] userWorkspaceArr = userWorkspace.split(USER_WORKSPACE_SPLIT);
                    String user = userWorkspaceArr[0];
                    String workspaceId = userWorkspaceArr[1];
                    sendToWorkspace(user, workspaceId, messageStr, method);
                });
    }

    /**
     * 发送观察者消息给用户
     * @param sessionId
     * @param messageStr
     */
    public void sendNativeObserverMessage(String sessionId, String messageStr) {
        sendWithSessionId(sessionId, messageStr, "agent/observer");
    }

    /**
     * 发送工具类消息给用户
     * @param sessionId
     * @param messageStr
     */
    public String sendNativeActionMessage(String sessionId, String messageStr) {
        sendWithSessionId(sessionId, messageStr, "agent/action");
        return "STOP";
    }

    /**
     * 添加session与请求id的映射关系
     * @param user
     * @param workspace
     * @param sessionId
     */
    public void addSessionRequest(String user, String workspace, String sessionId) {
        sessionToUserWorkspaceMap.put(sessionId, user + USER_WORKSPACE_SPLIT + workspace);
    }

    /**
     * 移除session与请求id的映射关系
     * @param sessionId
     */
    public void removeSessionRequest(String sessionId) {
        sessionToUserWorkspaceMap.remove(sessionId);
    }


    // ------------------------------ ↑ native ↓ stomp ------------------------------

    //
//    @PostConstruct
//    public void init() {
//        log.info("SessionManager init");
//    }
//
//    @Resource
    private SimpMessagingTemplate messagingTemplate;

    private Map<String, String> userToReqMap = new ConcurrentHashMap<>();
    private Map<String, String> reqToUserMap = new ConcurrentHashMap<>();

    /**
     * 添加session与请求id的映射关系
     * @param user
     * @param reqId
     */
    public void addSessionRequest(String user, String reqId) {
        userToReqMap.put(user, reqId);
        reqToUserMap.put(reqId, user);
    }

    /**
     * 移除session与请求id的映射关系
     * @param user
     * @param reqId
     */
    public void removeSessionRequest(String user, String reqId) {
        userToReqMap.remove(user);
        reqToUserMap.remove(reqId);
    }

    /**
     * 发送观察者消息给用户
     * @param reqId
     * @param messageStr
     */
    public void sendObserverMessage(String reqId, String messageStr) {
        String user = reqToUserMap.get(reqId);
//        messagingTemplate.convertAndSendToUser(user, "/queue/observer", messageStr);
        String messageId = UUID.randomUUID().toString();
        sendLargeMessage(user, "/queue/observer", messageStr, messageId);
        log.info("sendObserverMessage user: {}, messageId: {}, messageStr:{}", user, messageId, messageStr);
    }

    /**
     * 发送工具类消息给用户
     */
    public String sendToolMessage(String reqId, String messageStr) {
        String user = reqToUserMap.get(reqId);
//        messagingTemplate.convertAndSendToUser(user, "/queue/action", messageStr);
        String messageId = UUID.randomUUID().toString();
        sendLargeMessage(user, "/queue/action", messageStr, messageId);
        log.info("sendToolMessage user: {}, messageId: {}, messageStr:{}", user, messageId, messageStr);
        return "STOP";
    }

    /**
     * 发送工具类消息给用户
     */
    public String sendToolMessageByUser(String user, String messageStr) {
//        messagingTemplate.convertAndSendToUser(user, "/queue/action", messageStr);
        String messageId = UUID.randomUUID().toString();
        sendLargeMessage(user, "/queue/action", messageStr, messageId);
        log.info("sendToolMessage user: {}, messageId: {}, messageStr:{}", user, messageId, messageStr);
        return "STOP";
    }

    /**
     * 通过请求Id获取用户信息
     * @param requestId
     * @return
     */
    public String getUserByRequestId(String requestId) {
        return reqToUserMap.get(requestId);
    }

    /**
     * 根据用户获取请求ID
     * @param username
     * @return
     */
    public String getRequestIdByUser(String username) {
        return userToReqMap.get(username);
    }

    public void sendLargeMessage(String user, String destination, String messageStr, String messageId) {
        byte[] messageBytes = messageStr.getBytes(StandardCharsets.UTF_8);
        // 分片大小（例如1MB）
        int chunkSize = 1024;
        // 计算总分片数
        int totalChunks = (int) Math.ceil((double) messageBytes.length / chunkSize);

        for (int i = 0; i < totalChunks; i++) {
            // 计算当前分片的起始和结束位置
            int start = i * chunkSize;
            int end = Math.min(start + chunkSize, messageBytes.length);
            byte[] chunkData = Arrays.copyOfRange(messageBytes, start, end);

            // 构造分片消息
            ChunkedMessage chunk = new ChunkedMessage();
            chunk.setMessageId(messageId);
            chunk.setTotalChunks(totalChunks);
            chunk.setChunkIndex(i);
            chunk.setData(chunkData);

            // 发送到指定主题（假设主题为 `/topic/large-file`）
            messagingTemplate.convertAndSendToUser(user, destination, JSON.toJSONString(chunk));
            log.info("sendLargeMessage user: {}, messageId: {}, chunkIndex: {}, totalChunks: {}",
                    user, messageId, i, totalChunks);
        }
    }

    @Data
    public class ChunkedMessage {
        private String messageId;  // 唯一标识一个大消息（所有分片共享同一个ID）
        private int totalChunks;   // 总分片数
        private int chunkIndex;    // 当前分片索引（从0开始）
        private byte[] data;       // 分片数据内容
    }
}
