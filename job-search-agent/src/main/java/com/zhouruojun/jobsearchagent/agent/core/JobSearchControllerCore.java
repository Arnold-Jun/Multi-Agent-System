package com.zhouruojun.jobsearchagent.agent.core;

import com.alibaba.fastjson.JSONObject;
import com.zhouruojun.jobsearchagent.agent.AgentChatRequest;
import com.zhouruojun.jobsearchagent.agent.state.MainGraphState;
import com.zhouruojun.jobsearchagent.config.ParallelExecutionConfig;
import com.zhouruojun.jobsearchagent.config.CheckpointConfig;
import com.zhouruojun.jobsearchagent.config.FailureThresholdConfig;
import com.zhouruojun.jobsearchagent.agent.parser.SchedulerResponseParser;
import com.zhouruojun.jobsearchagent.service.OllamaService;
import com.zhouruojun.jobsearchagent.tools.JobSearchToolCollection;
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
import org.bsc.langgraph4j.state.StateSnapshot;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 求职智能体控制器核心类
 * 支持LangGraph4j图结构和求职工作流
 */
@Slf4j
@Component
public class JobSearchControllerCore {

    // LangGraph相关组件
    private Map<String, CompiledGraph<MainGraphState>> compiledGraphCache;
    private Map<String, StateGraph<MainGraphState>> stateGraphCache;
    private BaseCheckpointSaver checkpointSaver;
    
    // 核心依赖
    @Autowired
    private OllamaService ollamaService;
    
    @Autowired
    private ChatLanguageModel chatLanguageModel;
    
    @Autowired
    private CheckpointConfig checkpointConfig;
    
    // 求职工具集合
    @Autowired
    private JobSearchToolCollection toolCollection;
    
    // 并行执行配置
    @Autowired
    private ParallelExecutionConfig parallelExecutionConfig;
    
    // Scheduler相关依赖
    @Autowired
    private SchedulerResponseParser schedulerResponseParser;
    
    @Autowired
    private FailureThresholdConfig failureThresholdConfig;
    
    // 会话管理 - 从数据分析智能体迁移
    private final Map<String, List<ChatMessage>> sessionHistory = new ConcurrentHashMap<>();
    private final Map<String, Object> sessionCache = new ConcurrentHashMap<>();
    private final Map<String, Long> sessionAccessTimes = new ConcurrentHashMap<>();
    private static final long SESSION_TTL_MS = Duration.ofMinutes(45).toMillis();
    private volatile long lastCleanupTimestamp = 0L;
    private final Object cleanupLock = new Object();

    @PostConstruct
    public void init() throws GraphStateException {
        log.info("Initializing JobSearchControllerCore with LangGraph4j support");
        
        // 初始化缓存
        compiledGraphCache = new ConcurrentHashMap<>();
        stateGraphCache = new ConcurrentHashMap<>();
        
        // 初始化checkpoint saver
        checkpointSaver = new MemorySaver();
        
        log.info("JobSearchControllerCore initialized successfully");
    }

    public String processStreamReturnStr(AgentChatRequest request) {
        try {
            log.info("Processing job search request with LangGraph: {}", JSONObject.toJSON(request));
            
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
                
                // 获取分析结果
                if (state.getAnalysisResult().isPresent()) {
                    String analysisResult = state.getAnalysisResult().get();
                    resultBuilder.append("\n\n").append(analysisResult);
                }
            }
            
            String result = resultBuilder.toString();
            if (result.isEmpty()) {
                result = "求职分析完成，但未生成具体结果。";
            }
            
            // 更新会话历史
            updateSessionHistory(sessionId, request.getChat(), result);
            
            return result;
            
        } catch (Exception e) {
            log.error("Error processing job search request", e);
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
                    return buildGraph(username, sessionId);
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
     * 构建求职图
     */
    private StateGraph<MainGraphState> buildGraph(String username, String sessionId) throws GraphStateException {
        log.info("Building job search graph for session: {}", sessionId);
        
        return new com.zhouruojun.jobsearchagent.agent.builder.JobSearchGraphBuilder()
                .chatLanguageModel(chatLanguageModel)
                .toolCollection(toolCollection)
                .parallelExecutionConfig(parallelExecutionConfig)
                .checkpointSaver(checkpointSaver)
                .checkpointConfig(checkpointConfig)  // 传递配置对象
                .schedulerResponseParser(schedulerResponseParser)
                .failureThresholdConfig(failureThresholdConfig)
                .username(username)
                .requestId(sessionId)
                .build();
    }
    
    /**
     * 构建编译配置
     * 参考codewiz中cr-agent的interrupt配置
     */
    private CompileConfig buildCompileConfig() {
        return CompileConfig.builder()
                .checkpointSaver(checkpointSaver)
                // 暂时不设置interrupt，因为job-search-agent的主图流程不需要中断
                // 如果未来需要支持A2A中的复杂交互（如humanConfirm），可以添加：
                // .interruptBefore("actionWaiting", "humanConfirm", "humanPostConfirm")
                // .interruptAfter("userInput")
                .build();
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
     * 获取图状态信息（兼容性方法）
     */
    public Map<String, Object> getGraphStatus(String sessionId) {
        Map<String, Object> status = new ConcurrentHashMap<>();
        status.put("sessionId", sessionId);
        status.put("hasCompiledGraph", compiledGraphCache.containsKey(sessionId));
        status.put("hasStateGraph", stateGraphCache.containsKey(sessionId));
        status.put("checkpointSaverType", checkpointSaver.getClass().getSimpleName());
        
        return status;
    }

    /**
     * 重置求职流程
     */
    public void resetJobSearchFlow(String sessionId) {
        clearSessionHistory(sessionId);
        log.info("Reset job search flow for session: {}", sessionId);
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
                log.info("First call to job search agent for session: {}", sessionId);
                return processFirstA2aCall(sessionId, userMessage);
            } else {
                // 情况2：后续调用 - 从cache获取图状态并继续执行
                log.info("Resume call to job search agent for session: {}", sessionId);
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
            return processStreamReturnStr(request);
            
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
            return processStreamReturnStr(request);
            
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
                log.info("Removed expired job-search session {}", expiredSessionId);
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
                    com.zhouruojun.jobsearchagent.a2a.JobSearchTaskManager.sendTextEvent(
                        requestId, result, "__END__");
                    
                } catch (Exception e) {
                    log.error("Error processing A2A request", e);
                    // 发送错误事件
                    com.zhouruojun.jobsearchagent.a2a.JobSearchTaskManager.sendTextEvent(
                        requestId, "处理任务时发生错误: " + e.getMessage(), "__ERROR__");
                }
            });
            
        } catch (Exception e) {
            log.error("Error in processStreamWithA2a", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * 处理用户输入（用于A2A交互中的用户确认环节）
     * 参考codewiz中cr-agent的humanInput实现
     */
    public void humanInput(AgentChatRequest request) {
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            AsyncGenerator<NodeOutput<MainGraphState>> messages = null;
            String requestId = request.getRequestId();
            try {
                log.info("Processing humanInput for requestId: {}, chat: {}", requestId, request.getChat());
                
                RunnableConfig runnableConfig = RunnableConfig.builder()
                        .threadId(requestId)
                        .build();
                
                CompiledGraph<MainGraphState> graph = compiledGraphCache.get(requestId);
                if (graph == null) {
                    log.error("No compiled graph found for requestId: {}", requestId);
                    return;
                }
                
                StateSnapshot<MainGraphState> state = graph.getState(runnableConfig);
                
                // 更新状态：添加用户输入消息
                Map<String, Object> messages1 = Map.of("messages", UserMessage.from(request.getChat()));
                RunnableConfig newConfig = graph.updateState(state.config(), messages1);
                
                // 恢复执行
                messages = graph.stream(null, newConfig);
                
                // 处理流式响应并发送事件
                for (NodeOutput<MainGraphState> message : messages) {
                    log.info("humanInput processing node: {}", message.node());
                    // 可以在这里发送中间状态给调用方
                }
                
            } catch (Exception e) {
                log.error("Error processing humanInput", e);
                throw new RuntimeException(e);
            }
        });
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
