package com.zhouruojun.agentcore.agent.core;

import com.alibaba.fastjson.JSONObject;
import com.zhouruojun.agentcore.a2a.A2aClientManager;
import com.zhouruojun.agentcore.agent.AgentChatRequest;
import com.zhouruojun.agentcore.agent.builder.AgentGraphBuilder;
import com.zhouruojun.agentcore.agent.state.AgentMessageState;
import com.zhouruojun.agentcore.common.ContentFilter;
import com.zhouruojun.agentcore.config.AgentConstants;
import com.zhouruojun.agentcore.mcp.ToolProviderManager;
import com.zhouruojun.agentcore.service.OllamaService;
import com.zhouruojun.agentcore.a2a.A2aTaskManager;
import com.zhouruojun.agentcore.a2a.A2aTaskUpdate;
import com.zhouruojun.agentcore.spec.TaskArtifactUpdateEvent;
import com.zhouruojun.agentcore.spec.TaskSendParams;
import com.zhouruojun.agentcore.spec.TaskStatusUpdateEvent;
import com.zhouruojun.agentcore.spec.UpdateEvent;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.extern.slf4j.Slf4j;
import org.bsc.async.AsyncGenerator;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.NodeOutput;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import org.bsc.langgraph4j.state.StateSnapshot;

import java.util.concurrent.CompletableFuture;
import java.util.Map;
import java.util.List;
import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.annotation.PostConstruct;

/**
 * Agent控制器核心类 - 升级版本
 * 支持LangGraph4j图结构和完整的智能体协作流程
 */
@Slf4j
@Component
public class AgentControllerCore {

    // LangGraph相关组件
    private Map<String, CompiledGraph<AgentMessageState>> compiledGraphCache;
    private Map<String, StateGraph<AgentMessageState>> stateGraphCache;
    private BaseCheckpointSaver checkpointSaver;
    
    // 核心依赖
    @Autowired
    private ToolProviderManager toolProviderManager;
    
    @Autowired
    private OllamaService ollamaService;
    
    @Autowired
    @Lazy
    private A2aClientManager a2aClientManager;

    
    @Autowired
    @Qualifier("agentCoreChatLanguageModel")
    private ChatLanguageModel chatLanguageModel;
    
    // 会话管理
    private final Map<String, List<ChatMessage>> sessionHistory = new ConcurrentHashMap<>();
    private final Map<String, Object> sessionCache = new ConcurrentHashMap<>();
    private final Map<String, Long> sessionAccessTimes = new ConcurrentHashMap<>();
    
    // 会话清理相关
    private static final long SESSION_TTL_MS = Duration.ofHours(AgentConstants.SESSION_TTL_HOURS).toMillis();
    private volatile long lastCleanupTimestamp = 0;
    private final Object cleanupLock = new Object();

    @PostConstruct
    public void init() throws GraphStateException {
        log.info("Initializing AgentControllerCore with LangGraph4j support");
        
        // 初始化缓存
        compiledGraphCache = new ConcurrentHashMap<>();
        stateGraphCache = new ConcurrentHashMap<>();
        
        // 初始化checkpoint saver
        checkpointSaver = new MemorySaver();
        
        log.info("AgentControllerCore initialized successfully");
    }

    public String processStreamReturnStr(AgentChatRequest request) {
        try {
            log.info("Processing request with LangGraph: {}", JSONObject.toJSON(request));
            
            String sessionId = request.getSessionId();
            String chat = request.getChat();
            
            if (chat == null || chat.trim().isEmpty()) {
                return "错误: 聊天消息为空";
            }

            updateSessionAccess(sessionId);
            cleanupExpiredSessionsIfNeeded();

            AsyncGenerator<NodeOutput<AgentMessageState>> stream = processStream(request);
            
            StringBuilder resultBuilder = new StringBuilder();
            String finalResponse = "";
            
            AgentMessageState finalState = null;
            for (NodeOutput<AgentMessageState> output : stream) {
                log.info("Graph node output: {}", output.node());

                AgentMessageState state = output.state();
                finalState = state; // 保存最终状态
                
                if (state.agentResponse().isPresent()) {
                    finalResponse = state.agentResponse().get();
                }

                resultBuilder.append("节点: ").append(output.node())
                           .append(" -> ").append(finalResponse).append("\n");
            }

            String filteredResponse = ContentFilter.filterThinkingContent(finalResponse);
            
            String formattedResponse = filteredResponse;

            // 保存完整的对话历史（包括AI响应）
            if (finalState != null) {
                sessionHistory.put(sessionId, new ArrayList<>(finalState.messages()));
                log.info("Saved {} messages to session history for session {}", 
                        finalState.messages().size(), sessionId);
            }

            // 缓存会话信息
            sessionCache.put(sessionId, Map.of(
                "chat", chat,
                "aiResponse", finalResponse,
                "response", finalResponse,
                "timestamp", System.currentTimeMillis(),
                "executionTrace", resultBuilder.toString()
            ));
            
            return formattedResponse;
            
        } catch (Exception e) {
            log.error("Error processing request with graph", e);
            return "错误: " + e.getMessage();
        }
    }
    
    
    /**
     * 处理请求流程 - 使用LangGraph4j
     */
    private AsyncGenerator<NodeOutput<AgentMessageState>> processStream(AgentChatRequest request) 
            throws GraphStateException {
        
        String sessionId = request.getSessionId();
        String username = AgentConstants.SYSTEM_USERNAME;

        StateGraph<AgentMessageState> stateGraph = stateGraphCache.computeIfAbsent(sessionId, 
            key -> {
                try {
                    return buildGraph(username, sessionId);
                } catch (GraphStateException e) {
                    throw new RuntimeException("Failed to build graph", e);
                }
            });
        
        CompiledGraph<AgentMessageState> compiledGraph = compiledGraphCache.computeIfAbsent(sessionId,
            key -> {
                try {
                    return stateGraph.compile(buildCompileConfig());
                } catch (Exception e) {
                    throw new RuntimeException("Failed to compile graph", e);
                }
            });
        
        RunnableConfig runnableConfig = RunnableConfig.builder()
                .threadId(sessionId)
                .build();
        
        // 构建包含历史对话的初始状态
        Map<String, Object> initialState = buildInitialStateWithHistory(request, sessionId, username);
        
        return compiledGraph.stream(initialState, runnableConfig);
    }
    
    /**
     * 构建包含历史对话的初始状态
     */
    private Map<String, Object> buildInitialStateWithHistory(AgentChatRequest request, String sessionId, String username) {
        List<ChatMessage> allMessages = new ArrayList<>();
        
        // 1. 获取历史对话
        List<ChatMessage> historyMessages = getSessionHistory(sessionId);
        if (!historyMessages.isEmpty()) {
            log.info("Found {} historical messages for session {}", historyMessages.size(), sessionId);
            allMessages.addAll(historyMessages);
        }
        
        // 2. 添加当前用户消息
        ChatMessage currentUserMessage = UserMessage.from(request.getChat());
        allMessages.add(currentUserMessage);
        
        log.info("Total messages for session {}: {} (including current)", sessionId, allMessages.size());
        
        return Map.of(
            "messages", allMessages,
            "sessionId", sessionId,
            "username", username
        );
    }
    
    /**
     * 构建智能体图
     */
    private StateGraph<AgentMessageState> buildGraph(String username, String requestId) 
            throws GraphStateException {
        
        toolProviderManager.loadToolProvider(null, username, requestId);
        
        return new AgentGraphBuilder()
                .chatLanguageModel(chatLanguageModel)
                .toolProviderManager(toolProviderManager)
                .username(username)
                .requestId(requestId)
                .a2aClientManager(a2aClientManager)
                .build();
    }
    
    /**
     * 构建编译配置
     */
    private CompileConfig buildCompileConfig() {
        return CompileConfig.builder()
                .checkpointSaver(checkpointSaver)
                .interruptBefore("agentInvokeStateCheck")
                .interruptAfter("userInput")
                .build();
    }

    public void viewState(String sessionId) {
        Object sessionData = sessionCache.get(sessionId);
        if (sessionData != null) {
            log.info("Session {} state: {}", sessionId, JSONObject.toJSON(sessionData));
        } else {
            log.info("No session found for ID: {}", sessionId);
        }
    }

    public Map<String, Object> getSessionCache() {
        return sessionCache;
    }
    
    /**
     * 检查Ollama服务状态
     */
    public String checkOllamaStatus() {
        if (ollamaService.isAvailable()) {
            return "✅ Ollama服务正常运行";
        } else {
            return "❌ Ollama服务不可用";
        }
    }
    
    /**
     * 获取可用模型
     */
    public String getAvailableModels() {
        return ollamaService.getAvailableModels();
    }
    
    /**
     * 获取会话历史
     */
    public List<ChatMessage> getSessionHistory(String sessionId) {
        return sessionHistory.getOrDefault(sessionId, new ArrayList<>());
    }
    
    /**
     * 清除会话历史
     */
    public void clearSessionHistory(String sessionId) {
        sessionHistory.remove(sessionId);
        sessionCache.remove(sessionId);
        sessionAccessTimes.remove(sessionId);
        
        // 清除图缓存
        stateGraphCache.remove(sessionId);
        compiledGraphCache.remove(sessionId);
        
        log.info("Cleared session history and graph cache for: {}", sessionId);
    }
    
    /**
     * 获取所有活跃会话
     */
    public Map<String, Integer> getActiveSessions() {
        Map<String, Integer> sessions = new ConcurrentHashMap<>();
        for (Map.Entry<String, List<ChatMessage>> entry : sessionHistory.entrySet()) {
            sessions.put(entry.getKey(), entry.getValue().size());
        }
        return sessions;
    }
    
    /**
     * 获取图状态信息
     */
    public Map<String, Object> getGraphStatus() {
        return Map.of(
            "stateGraphCacheSize", stateGraphCache.size(),
            "compiledGraphCacheSize", compiledGraphCache.size(),
            "checkpointSaverType", checkpointSaver.getClass().getSimpleName(),
            "activeSessionsCount", sessionHistory.size()
        );
    }
    
    /**
     * 检查数据分析智能体健康状态
     */
    public boolean checkDataAnalysisAgentHealth() {
        try {
            return a2aClientManager.checkAgentHealth(AgentConstants.DATA_ANALYSIS_AGENT_NAME);
        } catch (Exception e) {
            log.error("Error checking data analysis agent health", e);
            return false;
        }
    }
    
    /**
     * 重新注册数据分析智能体
     */
    public void reregisterDataAnalysisAgent() {
        try {
            log.info("Attempting to reregister data-analysis-agent");
            a2aClientManager.registerAgent(AgentConstants.DATA_ANALYSIS_AGENT_NAME, AgentConstants.DATA_ANALYSIS_AGENT_URL);
            log.info("Successfully reregistered data-analysis-agent");
        } catch (Exception e) {
            log.error("Failed to reregister data-analysis-agent", e);
            throw new RuntimeException("Failed to reregister data-analysis-agent: " + e.getMessage(), e);
        }
    }
    
    /**
     * 清理所有图缓存
     */
    public void clearAllGraphCache() {
        stateGraphCache.clear();
        compiledGraphCache.clear();
        sessionAccessTimes.clear();
        log.info("Cleared all graph caches");
    }
    
    /**
     * 处理A2A AI消息事件
     * @param updateEvent 更新事件
     */
    public void a2aAiMessageOnEvent(UpdateEvent updateEvent) {
        TaskSendParams taskParams = A2aTaskManager.getInstance().getTaskParams(updateEvent.getId());
        String sessionId = taskParams.getSessionId();
        A2aTaskUpdate a2aTaskUpdate = new A2aTaskUpdate();
        a2aTaskUpdate.setTaskId(taskParams.getId());
        a2aTaskUpdate.setSessionId(sessionId);
        
        if (updateEvent instanceof TaskStatusUpdateEvent taskStatusUpdateEvent) {
            log.info("onEvent receive task status update: {}", taskStatusUpdateEvent);
            // 任务状态变化，可能是失败、成功、取消等。对当前流程进行下一步处理
            a2aTaskUpdate.setUpdateType("status");
            a2aTaskUpdate.setUpdateEvent(taskStatusUpdateEvent);
        } else if (updateEvent instanceof TaskArtifactUpdateEvent taskArtifactUpdateEvent) {
            // 纯制品的状态变化，与交互无关了。
            log.info("onEvent receive artifact update: {}", JSONObject.toJSONString(taskArtifactUpdateEvent));
            a2aTaskUpdate.setUpdateType("artifact");
            a2aTaskUpdate.setUpdateEvent(taskArtifactUpdateEvent);
        } else {
            log.error("receive task streaming response error, result:{}", JSONObject.toJSONString(updateEvent));
            throw new RuntimeException("receive task streaming response error" + JSONObject.toJSONString(updateEvent));
        }

        CompletableFuture.runAsync(() -> {
            try {
                RunnableConfig runnableConfig = RunnableConfig.builder()
                        .threadId(sessionId)
                        .build();
                CompiledGraph<AgentMessageState> graph = compiledGraphCache.get(sessionId);
                if (graph == null) {
                    log.warn("No graph found for sessionId: {}", sessionId);
                    return;
                }
                StateSnapshot<AgentMessageState> state = graph.getState(runnableConfig);

                Map<String, Object> messages1 = Map.of(
                        "messages", UserMessage.from(JSONObject.toJSONString(a2aTaskUpdate)),
                        "method", "a2a_update"
                );
                RunnableConfig newConfig = graph.updateState(state.getConfig(), messages1);
                AsyncGenerator<NodeOutput<AgentMessageState>> stream = graph.stream(null, newConfig);
                sendMessage(sessionId, stream);
            } catch (Exception e) {
                log.error("a2aAiMessageOnEvent error", e);
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * 恢复流式处理工具调用结果
     * @param toolResponse 工具调用响应
     * @param sessionId 会话ID
     */
    public void resumeStreamWithTools(Object toolResponse, String sessionId) {
        CompletableFuture.runAsync(() -> {
            AsyncGenerator<NodeOutput<AgentMessageState>> messages = null;
            try {
                RunnableConfig runnableConfig = RunnableConfig.builder()
                        .threadId(sessionId)
                        .build();
                CompiledGraph<AgentMessageState> graph = compiledGraphCache.get(sessionId);
                if (graph == null) {
                    log.warn("No graph found for sessionId: {}", sessionId);
                    return;
                }
                StateSnapshot<AgentMessageState> state = graph.getState(runnableConfig);

                Map<String, Object> messages1 = Map.of(
                        "messages", UserMessage.from(JSONObject.toJSONString(toolResponse)),
                        "method", "tool_call"
                );
                RunnableConfig newConfig = graph.updateState(state.getConfig(), messages1);
                messages = graph.stream(null, newConfig);
            } catch (Exception e) {
                log.error("resumeStreamWithTools error", e);
                throw new RuntimeException(e);
            }
            sendMessage(sessionId, messages);
        });
    }

    /**
     * 处理用户输入
     * @param request 用户请求
     */
    public void humanInput(AgentChatRequest request) {
        String sessionId = request.getSessionId();
        updateSessionAccess(sessionId);
        CompletableFuture.runAsync(() -> {
            AsyncGenerator<NodeOutput<AgentMessageState>> messages = null;
            try {
                RunnableConfig runnableConfig = RunnableConfig.builder()
                        .threadId(sessionId)
                        .build();
                CompiledGraph<AgentMessageState> graph = compiledGraphCache.get(sessionId);
                if (graph == null) {
                    log.warn("No graph found for sessionId: {}", sessionId);
                    return;
                }
                StateSnapshot<AgentMessageState> state = graph.getState(runnableConfig);

                Map<String, Object> messages1 = Map.of(
                        "messages", UserMessage.from(request.getChat()),
                        "method", "human_input"
                );
                RunnableConfig newConfig = graph.updateState(state.getConfig(), messages1);
                messages = graph.stream(null, newConfig);
            } catch (Exception e) {
                log.error("humanInput error", e);
                throw new RuntimeException(e);
            }
            sendMessage(sessionId, messages);
        });
    }

    /**
     * 处理用户确认
     * @param request 用户请求
     */
    public void humanConfirm(AgentChatRequest request) {
        String sessionId = request.getSessionId();
        updateSessionAccess(sessionId);
        CompletableFuture.runAsync(() -> {
            AsyncGenerator<NodeOutput<AgentMessageState>> messages = null;
            try {
                RunnableConfig runnableConfig = RunnableConfig.builder()
                        .threadId(sessionId)
                        .build();
                CompiledGraph<AgentMessageState> graph = compiledGraphCache.get(sessionId);
                if (graph == null) {
                    log.warn("No graph found for sessionId: {}", sessionId);
                    return;
                }
                StateSnapshot<AgentMessageState> state = graph.getState(runnableConfig);

                Map<String, Object> messages1 = Map.of(
                        "messages", UserMessage.from(request.getChat()),
                        "method", "human_confirm"
                );
                RunnableConfig newConfig = graph.updateState(state.getConfig(), messages1);
                messages = graph.stream(null, newConfig);
            } catch (Exception e) {
                log.error("humanConfirm error", e);
                throw new RuntimeException(e);
            }
            sendMessage(sessionId, messages);
        });
    }

    /**
     * 处理用户重放
     * @param sessionId 会话ID
     */
    public void humanReplay(String sessionId) {
        updateSessionAccess(sessionId);
        CompletableFuture.runAsync(() -> {
            AsyncGenerator<NodeOutput<AgentMessageState>> messages = null;
            try {
                RunnableConfig runnableConfig = RunnableConfig.builder()
                        .threadId(sessionId)
                        .build();
                CompiledGraph<AgentMessageState> graph = compiledGraphCache.get(sessionId);
                if (graph == null) {
                    log.warn("No graph found for sessionId: {}", sessionId);
                    return;
                }
                StateSnapshot<AgentMessageState> state = graph.getState(runnableConfig);
                // 获取历史状态，回退到上一个状态
                List<StateSnapshot<AgentMessageState>> stateHistory = new ArrayList<>(graph.getStateHistory(runnableConfig));
                StateSnapshot<AgentMessageState> replayState = null;
                for (StateSnapshot<AgentMessageState> snapshot : stateHistory) {
                    if (snapshot.state().messages().size() == state.state().messages().size() - 1) {
                        replayState = snapshot;
                        break;
                    }
                }
                if (replayState == null) {
                    log.error("humanReplay error, replayState is null");
                    return;
                }
                messages = graph.stream(null, replayState.config());
            } catch (Exception e) {
                log.error("humanReplay error", e);
                throw new RuntimeException(e);
            }
            sendMessage(sessionId, messages);
        });
    }

    /**
     * 发送消息到客户端
     * @param sessionId 会话ID
     * @param messages 消息流
     */
    private void sendMessage(String sessionId, AsyncGenerator<NodeOutput<AgentMessageState>> messages) {
        try {
            for (NodeOutput<AgentMessageState> output : messages) {
                AgentMessageState currentState = output.state();
                sessionHistory.put(sessionId, new ArrayList<>(currentState.messages()));
                currentState.agentResponse().ifPresent(response -> sessionCache.put(sessionId, Map.of(
                        "agent_response", response,
                        "node", output.node(),
                        "timestamp", System.currentTimeMillis())));
                updateSessionAccess(sessionId);
                log.info("Streaming update for sessionId: {}, node: {}", sessionId, output.node());
            }
        } catch (Exception e) {
            log.error("Error sending message for sessionId: {}", sessionId, e);
        }
    }

    private void updateSessionAccess(String sessionId) {
        sessionAccessTimes.put(sessionId, System.currentTimeMillis());
    }

    private void cleanupExpiredSessionsIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - lastCleanupTimestamp < SESSION_TTL_MS / 4) {
            return;
        }
        synchronized (cleanupLock) {
            if (now - lastCleanupTimestamp < SESSION_TTL_MS / 4) {
                return;
            }
            cleanupExpiredSessions(now);
            lastCleanupTimestamp = now;
        }
    }

    private void cleanupExpiredSessions(long now) {
        sessionAccessTimes.entrySet().removeIf(entry -> {
            if (now - entry.getValue() > SESSION_TTL_MS) {
                String expiredSessionId = entry.getKey();
                sessionHistory.remove(expiredSessionId);
                sessionCache.remove(expiredSessionId);
                stateGraphCache.remove(expiredSessionId);
                compiledGraphCache.remove(expiredSessionId);
                log.info("Removed expired session {}", expiredSessionId);
                return true;
            }
            return false;
        });
    }
}