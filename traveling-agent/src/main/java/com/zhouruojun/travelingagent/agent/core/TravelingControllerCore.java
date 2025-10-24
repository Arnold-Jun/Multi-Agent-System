package com.zhouruojun.travelingagent.agent.core;

import com.alibaba.fastjson.JSONObject;
import com.zhouruojun.travelingagent.agent.builder.TravelingGraphBuilder;
import com.zhouruojun.travelingagent.agent.dto.AgentChatRequest;
import com.zhouruojun.travelingagent.agent.state.MainGraphState;
import com.zhouruojun.travelingagent.agent.state.subgraph.SubgraphStateManager;
import com.zhouruojun.travelingagent.config.ParallelExecutionConfig;
import com.zhouruojun.travelingagent.config.CheckpointConfig;
import com.zhouruojun.travelingagent.agent.parser.SchedulerResponseParser;
import com.zhouruojun.travelingagent.service.OllamaService;
import com.zhouruojun.travelingagent.mcp.TravelingToolProviderManager;
import com.zhouruojun.travelingagent.a2a.TravelingTaskManager;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.NodeOutput;
import org.bsc.langgraph4j.state.StateSnapshot;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;
import org.bsc.async.AsyncGenerator;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver;
import org.bsc.langgraph4j.checkpoint.MemorySaver;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 旅游智能体控制器核心类
 * 支持LangGraph4j图结构和旅游工作流
 */
@Slf4j
@Component
public class TravelingControllerCore {

    // LangGraph相关组件
    private Map<String, CompiledGraph<MainGraphState>> compiledGraphCache;
    private Map<String, StateGraph<MainGraphState>> stateGraphCache;
    private BaseCheckpointSaver checkpointSaver;
    
    // 核心依赖
    @Autowired
    private OllamaService ollamaService;
    
    @Autowired
    private SimpMessagingTemplate messagingTemplate;
    
    @Autowired
    @org.springframework.beans.factory.annotation.Qualifier("travelingChatLanguageModel")
    private ChatLanguageModel chatLanguageModel;
    
    @Autowired
    private CheckpointConfig checkpointConfig;
    
    
    // 并行执行配置
    @Autowired
    private ParallelExecutionConfig parallelExecutionConfig;
    
    // Scheduler相关依赖
    @Autowired
    private SchedulerResponseParser schedulerResponseParser;
    
    // 子图状态管理器
    @Autowired
    private SubgraphStateManager subgraphStateManager;
    
    @Autowired
    @org.springframework.context.annotation.Lazy
    private TravelingTaskManager travelingTaskManager;
    
    // 工具提供者管理器
    @Autowired
    private TravelingToolProviderManager toolProviderManager;
    
    
    // 会话管理 - 从数据分析智能体迁移
    private final Map<String, List<ChatMessage>> sessionHistory = new ConcurrentHashMap<>();
    private final Map<String, Object> sessionCache = new ConcurrentHashMap<>();
    private final Map<String, Long> sessionAccessTimes = new ConcurrentHashMap<>();
    private static final long SESSION_TTL_MS = Duration.ofMinutes(45).toMillis();
    private volatile long lastCleanupTimestamp = 0L;
    private final Object cleanupLock = new Object();

    @PostConstruct
    public void init() throws GraphStateException {
        log.info("Initializing TravelingControllerCore with LangGraph4j support");
        
        // 初始化缓存
        compiledGraphCache = new ConcurrentHashMap<>();
        stateGraphCache = new ConcurrentHashMap<>();
        
        // 初始化checkpoint saver
        checkpointSaver = new MemorySaver();
        
        log.info("TravelingControllerCore initialized successfully");
    }

    public String processStreamReturnStr(AgentChatRequest request) {
        try {
            log.info("Processing travel planning request with LangGraph: {}", JSONObject.toJSON(request));
            
            String sessionId = request.getSessionId();
            String chat = request.getChat();
            
            if (chat == null || chat.trim().isEmpty()) {
                return "错误: 聊天消息为空";
            }

            updateSessionAccess(sessionId);
            cleanupExpiredSessionsIfNeeded();

            // 使用图处理流程
            AsyncGenerator<NodeOutput<MainGraphState>> stream = processStream(request);
            
            StringBuilder resultBuilder = new StringBuilder();
            String finalResponse = "";
            
            // 处理流式输出
            for (NodeOutput<MainGraphState> output : stream) {
                MainGraphState state = output.state();
                
                // 获取最终响应
                if (state.getFinalResponse().isPresent()) {
                    finalResponse = state.getFinalResponse().get();
                    resultBuilder.append(finalResponse);
                }
            }
            
            String result = resultBuilder.toString();
            if (result.isEmpty()) {
                result = "旅游规划完成，但未获取到具体结果。";
            }
            
            // 更新会话历史
            updateSessionHistory(sessionId, request.getChat(), result);
            
            return result;
            
        } catch (Exception e) {
            log.error("Error processing travel planning request", e);
            return "错误: " + e.getMessage();
        }
    }
    
    /**
     * 处理请求流程 - 使用LangGraph4j
     */
    private AsyncGenerator<NodeOutput<MainGraphState>> processStream(AgentChatRequest request) 
            throws GraphStateException {
        
        String sessionId = request.getSessionId();
        String username = "user"; // 可以从request中获取
        
        // 获取或构建状态图
        StateGraph<MainGraphState> stateGraph = stateGraphCache.computeIfAbsent(sessionId, 
            key -> {
                try {
                    return buildGraph(request);
                } catch (GraphStateException e) {
                    throw new RuntimeException("Failed to build graph", e);
                }
            });
        
        // 获取或编译图
        CompiledGraph<MainGraphState> compiledGraph = compiledGraphCache.computeIfAbsent(sessionId,
            key -> {
                try {
                    return stateGraph.compile(buildCompileConfig());
                } catch (Exception e) {
                    throw new RuntimeException("Failed to compile graph", e);
                }
            });
        
        // 构建运行配置
        RunnableConfig runnableConfig = RunnableConfig.builder()
                .threadId(sessionId)
                .build();
        
        // 构建初始状态
        Map<String, Object> initialState = Map.of(
            "messages", UserMessage.from(request.getChat()),
            "originalUserQuery", request.getChat(),
            "sessionId", sessionId,
            "username", username
        );
        
        // 执行图流程
        return compiledGraph.stream(initialState, runnableConfig);
    }
    
    /**
     * 构建旅游图
     */
    private StateGraph<MainGraphState> buildGraph(AgentChatRequest request) throws GraphStateException {
        String sessionId = request.getSessionId();
        log.info("Building travel planning graph for session: {}", sessionId);
        
        return new TravelingGraphBuilder()
                .chatLanguageModel(chatLanguageModel)
                .toolProviderManager(toolProviderManager)  // 传递工具提供者管理器
                .parallelExecutionConfig(parallelExecutionConfig)
                .checkpointSaver(checkpointSaver)
                .checkpointConfig(checkpointConfig)  // 传递配置对象
                .schedulerResponseParser(schedulerResponseParser)
                .subgraphStateManager(subgraphStateManager)  // 传递子图状态管理器
                .build();
    }
    
    /**
     * 构建编译配置
     */
    private CompileConfig buildCompileConfig() {
        return CompileConfig.builder()
                .checkpointSaver(checkpointSaver)
                .interruptAfter("userInput")  // 在userInput节点后打断执行
                .build();
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
        
        // 清除子图状态
        if (subgraphStateManager != null) {
            subgraphStateManager.clearSessionStates(sessionId);
        }
        
        log.info("Cleared session history, graph cache and subgraph states for: {}", sessionId);
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
            "checkpointSaverType", "MemorySaver",
            "activeSessionsCount", sessionHistory.size()
        );
    }

    /**
     * 获取活跃会话数量
     */
    public int getActiveSessionCount() {
        return sessionHistory.size();
    }

    /**
     * A2A同步处理方法
     * 处理来自主智能体的A2A调用请求
     * 
     * @param sessionId 会话ID
     * @param userMessage 用户消息
     * @return 处理结果
     */
    public String processA2aSync(String sessionId, String userMessage) {
        updateSessionAccess(sessionId);
        cleanupExpiredSessionsIfNeeded();
        try {
            log.info("Processing A2A sync request: sessionId={}, message={}", sessionId, userMessage);
            
            // 检查是否是第一次调用（cache中没有图）
            boolean isFirstCall = !compiledGraphCache.containsKey(sessionId);
            
            if (isFirstCall) {
                // 情况1：第一次调用 - 初始化状态图和编译图
                log.info("First call to travel planning agent for session: {}", sessionId);
                return processFirstA2aCall(sessionId, userMessage);
            } else {
                // 情况2：后续调用 - 从cache获取图状态并继续执行
                log.info("Resume call to travel planning agent for session: {}", sessionId);
                return processResumeA2aCall(sessionId, userMessage);
            }
            
        } catch (Exception e) {
            log.error("Error processing A2A sync request: sessionId={}", sessionId, e);
            return "Error: 处理A2A请求时发生错误: " + e.getMessage();
        }
    }
    
    /**
     * 处理第一次A2A调用
     */
    private String processFirstA2aCall(String sessionId, String userMessage) {
        try {
            // 创建AgentChatRequest对象
            AgentChatRequest request = new AgentChatRequest();
            request.setSessionId(sessionId);
            request.setChat(userMessage);
            
            // 使用标准的流式处理方法
            String result = processStreamReturnStr(request);
            updateSessionAccess(sessionId);
            cleanupExpiredSessionsIfNeeded();
            return result;
            
        } catch (Exception e) {
            log.error("Error processing first A2A call: sessionId={}", sessionId, e);
            return "Error: 处理第一次A2A调用时发生错误: " + e.getMessage();
        }
    }
    
    /**
     * 处理恢复A2A调用
     */
    private String processResumeA2aCall(String sessionId, String userMessage) {
        try {
            // 创建AgentChatRequest对象
            AgentChatRequest request = new AgentChatRequest();
            request.setSessionId(sessionId);
            request.setChat(userMessage);
            
            // 使用标准的流式处理方法
            String result = processStreamReturnStr(request);
            updateSessionAccess(sessionId);
            cleanupExpiredSessionsIfNeeded();
            return result;
            
        } catch (Exception e) {
            log.error("Error processing resume A2A call: sessionId={}", sessionId, e);
            return "Error: 处理恢复A2A调用时发生错误: " + e.getMessage();
        }
    }

    /**
     * 更新会话历史
     */
    private void updateSessionHistory(String sessionId, String userMessage, String response) {
        List<ChatMessage> history = sessionHistory.computeIfAbsent(sessionId, k -> new ArrayList<>());
        history.add(UserMessage.from(userMessage));
        history.add(dev.langchain4j.data.message.AiMessage.from(response));
        
        // 限制历史记录长度
        if (history.size() > 100) {
            history.subList(0, history.size() - 100).clear();
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
                log.info("Removed expired travel-planning session {}", expiredSessionId);
                return true;
            }
            return false;
        });
    }

    /**
     * 处理来自A2A的流式请求
     * @param requestId 请求ID（任务ID）
     * @param sessionId 会话ID
     * @param message A2A消息
     * @param userEmail 用户邮箱
     */
    public void processStreamWithA2a(String requestId,
                                     String sessionId,
                                     com.zhouruojun.a2acore.spec.Message message,
                                     String userEmail) {
        try {
            log.info("Processing A2A request - requestId: {}, sessionId: {}", requestId, sessionId);
            
            // 从A2A消息中提取文本内容
            List<com.zhouruojun.a2acore.spec.Part> parts = message.getParts();
            if (parts.isEmpty() || !(parts.get(0) instanceof com.zhouruojun.a2acore.spec.TextPart)) {
                throw new IllegalArgumentException("Invalid A2A message: no text part found");
            }
            
            com.zhouruojun.a2acore.spec.TextPart textPart = (com.zhouruojun.a2acore.spec.TextPart) parts.get(0);
            String taskInstruction = textPart.getText();
            
            log.info("A2A task instruction: {}", taskInstruction);
            
            // 构建AgentChatRequest
            AgentChatRequest request = new AgentChatRequest();
            request.setSessionId(requestId);  // 使用requestId作为sessionId
            request.setRequestId(requestId);
            request.setChat(taskInstruction);
            
            // 异步处理
            java.util.concurrent.CompletableFuture.runAsync(() -> {
                try {
                    // 处理并发送事件
                    String result = processStreamReturnStr(request);
                    
                    // 使用静态方法发送最终响应
                    com.zhouruojun.travelingagent.a2a.TravelingTaskManager.sendTextEvent(
                        requestId, result, "__END__");
                    
                } catch (Exception e) {
                    log.error("Error processing A2A request", e);
                    // 发送错误事件
                    com.zhouruojun.travelingagent.a2a.TravelingTaskManager.sendTextEvent(
                        requestId, "处理任务时发生错误: " + e.getMessage(), "__ERROR__");
                }
            });
            
        } catch (Exception e) {
            log.error("Error in processStreamWithA2a", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * 处理用户输入（从userInput节点恢复执行）
     * 
     * 工作流程：
     * 1. 获取当前会话的图状态（从userInput节点打断的位置）
     * 2. 更新状态，添加用户输入消息
     * 3. 从打断点恢复执行图
     * 4. 处理后续节点的输出
     */
    public void humanInput(AgentChatRequest request) {
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            String sessionId = request.getSessionId();
            updateSessionAccess(sessionId);
            
            try {
                log.info("Processing humanInput for sessionId: {}, chat: {}", sessionId, request.getChat());
                
                CompiledGraph<MainGraphState> graph = compiledGraphCache.get(sessionId);
                if (graph == null) {
                    log.error("No compiled graph found for sessionId: {}", sessionId);
                    return;
                }
                
                RunnableConfig runnableConfig = RunnableConfig.builder()
                        .threadId(sessionId)
                        .build();
                
                // 获取当前状态快照（从userInput打断的位置）
                org.bsc.langgraph4j.state.StateSnapshot<MainGraphState> stateSnapshot = 
                        graph.getState(runnableConfig);

                // 更新状态：添加用户输入消息
                Map<String, Object> updateData = Map.of(
                        "messages", UserMessage.from(request.getChat()),
                        "method", "human_input",
                        "userInputRequired", false  // 清除userInputRequired标志
                );
                
                // 更新状态并获取新的配置
                RunnableConfig newConfig = graph.updateState(stateSnapshot.config(), updateData);
                
                // 从打断点恢复执行（传null表示从当前状态继续）
                AsyncGenerator<NodeOutput<MainGraphState>> messages = graph.stream(null, newConfig);
                
                // 处理流式响应
                String finalResponse = "";
                for (NodeOutput<MainGraphState> output : messages) {
                    log.info("humanInput processing node: {}", output.node());
                    
                    MainGraphState state = output.state();
                    
                    // 获取最终响应
                    if (state.getFinalResponse().isPresent()) {
                        finalResponse = state.getFinalResponse().get();
                    }
                }
                
                // 更新会话历史
                if (!finalResponse.isEmpty()) {
                    updateSessionHistory(sessionId, request.getChat(), finalResponse);
                }
                
                log.info("humanInput completed for sessionId: {}", sessionId);
                
            } catch (Exception e) {
                log.error("Error processing humanInput", e);
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * 同步处理用户输入
     * @param request 用户请求
     * @return 处理结果
     */
    public String humanInputSync(AgentChatRequest request) {
        String sessionId = request.getSessionId();
        updateSessionAccess(sessionId);
        
        try {
            log.info("Processing humanInputSync for sessionId: {}, chat: {}", sessionId, request.getChat());
            
            // 将用户输入存储到 TravelingTaskManager 中
            String userInput = request.getChat();
            if (userInput == null || userInput.trim().isEmpty()) {
                log.warn("No user input in request for sessionId: {}", sessionId);
                return "错误：用户输入为空，请提供有效输入";
            }
            
            // 存储用户输入到 TravelingTaskManager
            travelingTaskManager.setUserInput(sessionId, userInput);
            log.info("Stored user input in TravelingTaskManager: {}", userInput);
            
            CompiledGraph<MainGraphState> graph = compiledGraphCache.get(sessionId);
            if (graph == null) {
                log.error("No compiled graph found for sessionId: {}", sessionId);
                return "错误：未找到会话状态，请重新开始对话";
            }
            
            RunnableConfig runnableConfig = RunnableConfig.builder()
                    .threadId(sessionId)
                    .build();
            
            // 获取当前状态快照（从userInput打断的位置）
            org.bsc.langgraph4j.state.StateSnapshot<MainGraphState> stateSnapshot = 
                    graph.getState(runnableConfig);

            // 使用从 TravelingTaskManager 获取的用户输入
            // 添加用户输入到历史，但不覆盖原始查询
            Map<String, Object> updateData = new HashMap<>();
            updateData.put("messages", UserMessage.from(userInput));
            updateData.put("method", "human_input");
            updateData.put("userInputRequired", false);  // 清除userInputRequired标志
            updateData.put("userInput", userInput);  // 添加用户输入，供Scheduler使用
            
            // 更新状态并获取新的配置
            RunnableConfig newConfig = graph.updateState(stateSnapshot.config(), updateData);
            
            // 从打断点恢复执行（传null表示从当前状态继续）
            AsyncGenerator<NodeOutput<MainGraphState>> messages = graph.stream(null, newConfig);
            
            // 处理流式响应
            StringBuilder resultBuilder = new StringBuilder();
            String finalResponse = "";
            
            for (NodeOutput<MainGraphState> output : messages) {
                log.info("humanInputSync processing node: {}", output.node());
                
                MainGraphState state = output.state();
                
                // 获取最终响应
                if (state.getFinalResponse().isPresent()) {
                    finalResponse = state.getFinalResponse().get();
                    resultBuilder.append(finalResponse);
                }
            }
            
            String result = resultBuilder.toString();
            if (result.isEmpty()) {
                result = "处理完成";
            }
            
            // 更新会话历史
            updateSessionHistory(sessionId, userInput, result);
            
            log.info("humanInputSync completed for sessionId: {}", sessionId);
            
            return result;
            
        } catch (Exception e) {
            log.error("Error processing humanInputSync", e);
            return "错误：处理用户输入失败 - " + e.getMessage();
        }
    }

    /**
     * 处理WebSocket聊天请求
     * @param request 聊天请求
     * @param userEmail 用户邮箱
     */
    public void processStreamWithWs(AgentChatRequest request, String userEmail) {
        CompletableFuture.runAsync(() -> {
            String sessionId = request.getSessionId();
            updateSessionAccess(sessionId);
            
            try {
                // 处理流式响应
                AsyncGenerator<NodeOutput<MainGraphState>> stream = processStream(request);
                
                // 处理每个节点的输出
                for (NodeOutput<MainGraphState> output : stream) {
                    MainGraphState state = output.state();
                    String nodeName = output.node();

                    // 检查是否需要用户输入
                    if (state.getValue("userInputRequired").isPresent() && 
                        (Boolean) state.getValue("userInputRequired").get()) {
                        // 图已在userInput节点暂停，等待用户输入
                        // 发送用户输入请求到前端
                        String prompt = state.getValue("subgraphTaskDescription").isPresent() ? 
                            (String) state.getValue("subgraphTaskDescription").get() : 
                            "需要您提供更多信息以继续...";
                        
                        sendUserInputRequest(sessionId, prompt);
                        break;
                    }
                    
                    // 发送最终响应
                    if (state.getFinalResponse().isPresent()) {
                        String finalResponse = state.getFinalResponse().get();
                        log.info("Final response for WebSocket: {}", finalResponse);
                        updateSessionHistory(sessionId, request.getChat(), finalResponse);
                        
                        // 发送响应到前端
                        sendResponse(sessionId, finalResponse);
                    }
                }
                
                log.info("WebSocket chat processing completed for sessionId: {}", sessionId);
                
            } catch (Exception e) {
                log.error("Error processing WebSocket chat request", e);
                sendError(sessionId, "处理请求时发生错误: " + e.getMessage());
            }
        });
    }

    /**
     * 处理WebSocket用户输入
     * @param request 用户输入请求
     * @param userEmail 用户邮箱
     */
    public void humanInputWithWs(AgentChatRequest request, String userEmail) {
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            String sessionId = request.getSessionId();
            updateSessionAccess(sessionId);
            
            try {
                log.info("Processing humanInput - sessionId: {}, userEmail: {}", sessionId, userEmail);
                
                // 存储用户输入到 TravelingTaskManager
                String userInput = request.getChat();
                travelingTaskManager.setUserInput(sessionId, userInput);
                
                CompiledGraph<MainGraphState> graph = compiledGraphCache.get(sessionId);
                if (graph == null) {
                    log.error("No compiled graph found for sessionId: {}", sessionId);
                    return;
                }
                
                RunnableConfig runnableConfig = RunnableConfig.builder()
                        .threadId(sessionId)
                        .build();
                
                // 获取当前状态快照（从userInput打断的位置）
                StateSnapshot<MainGraphState> stateSnapshot =
                        graph.getState(runnableConfig);

                // 更新状态：添加用户输入消息
                Map<String, Object> updateData = new HashMap<>();
                updateData.put("messages", UserMessage.from(userInput));
                updateData.put("method", "human_input");
                updateData.put("userInputRequired", false);
                updateData.put("userInput", userInput);
                
                // 更新用户输入历史
                MainGraphState currentState = stateSnapshot.state();
                List<String> userQueryHistory = currentState.getUserQueryHistory();
                userQueryHistory.add(userInput);
                updateData.put("userQueryHistory", userQueryHistory);
                
                // 更新状态并获取新的配置
                RunnableConfig newConfig = graph.updateState(stateSnapshot.config(), updateData);
                
                // 从打断点恢复执行
                AsyncGenerator<NodeOutput<MainGraphState>> messages = graph.stream(null, newConfig);
                
                // 处理流式响应
                String finalResponse = "";
                boolean needsUserInput = false;
                String userInputPrompt = "";
                
                for (NodeOutput<MainGraphState> output : messages) {
                    MainGraphState state = output.state();
                    String nodeName = output.node();

                    // 检查是否又需要用户输入
                    if (state.getValue("userInputRequired").isPresent() && 
                        (Boolean) state.getValue("userInputRequired").get()) {
                        log.info("Graph paused again at userInput node for sessionId: {}", sessionId);
                        needsUserInput = true;
                        
                        // 获取用户输入提示
                        userInputPrompt = state.getValue("subgraphTaskDescription").isPresent() ? 
                            (String) state.getValue("subgraphTaskDescription").get() : 
                            "需要您提供更多信息以继续...";
                        break;
                    }
                    
                    // 只有在不需要用户输入时才收集最终响应
                    if (state.getFinalResponse().isPresent()) {
                        finalResponse = state.getFinalResponse().get();
                        updateSessionHistory(sessionId, userInput, finalResponse);
                    }
                }
                
                // 根据情况发送消息
                if (needsUserInput) {
                    // 需要用户输入，发送用户输入请求
                    sendUserInputRequest(sessionId, userInputPrompt);
                } else if (!finalResponse.isEmpty()) {
                    // 有最终响应，发送响应
                    sendResponse(sessionId, finalResponse);
                }

            } catch (Exception e) {
                log.error("Error processing WebSocket humanInput", e);
                sendError(sessionId, "处理用户输入时发生错误: " + e.getMessage());
            }
        });
    }

    /**
     * 发送响应消息到前端
     */
    private void sendResponse(String sessionId, String content) {
        try {
            Map<String, Object> message = new HashMap<>();
            message.put("type", "response");
            message.put("content", content);
            message.put("sessionId", sessionId);
            
            // 使用广播方式发送，前端通过sessionId过滤
            messagingTemplate.convertAndSend("/topic/reply", message);
            log.info("Sent response to sessionId {}: {}", sessionId, content);
        } catch (Exception e) {
            log.error("Failed to send response to sessionId: {}", sessionId, e);
        }
    }

    /**
     * 发送用户输入请求到前端
     */
    private void sendUserInputRequest(String sessionId, String prompt) {
        try {
            Map<String, Object> message = new HashMap<>();
            message.put("type", "userInputRequired");
            message.put("prompt", prompt);
            message.put("sessionId", sessionId);
            
            // 使用广播方式发送，前端通过sessionId过滤
            messagingTemplate.convertAndSend("/topic/reply", message);
            log.info("Sent user input request to sessionId {}: {}", sessionId, prompt);
        } catch (Exception e) {
            log.error("Failed to send user input request to sessionId: {}", sessionId, e);
        }
    }

    /**
     * 发送错误消息到前端
     */
    private void sendError(String sessionId, String error) {
        try {
            Map<String, Object> message = new HashMap<>();
            message.put("type", "error");
            message.put("error", error);
            message.put("sessionId", sessionId);
            
            // 使用广播方式发送，前端通过sessionId过滤
            messagingTemplate.convertAndSend("/topic/error", message);
            log.info("Sent error to sessionId {}: {}", sessionId, error);
        } catch (Exception e) {
            log.error("Failed to send error to sessionId: {}", sessionId, e);
        }
    }

    /**
     * 检查Ollama状态
     */
    public String checkOllamaStatus() {
        return ollamaService.checkOllamaStatus();
    }

    /**
     * 获取可用模型
     */
    public String getAvailableModels() {
        return ollamaService.getAvailableModels();
    }
}
