package com.zhouruojun.dataanalysisagent.agent.core;

import com.alibaba.fastjson.JSONObject;
import com.zhouruojun.a2acore.spec.DataPart;
import com.zhouruojun.a2acore.spec.FilePart;
import com.zhouruojun.a2acore.spec.Message;
import com.zhouruojun.a2acore.spec.Part;
import com.zhouruojun.a2acore.spec.TextPart;
import com.zhouruojun.dataanalysisagent.agent.AgentChatRequest;
import com.zhouruojun.dataanalysisagent.agent.builder.DataAnalysisGraphBuilder;
import com.zhouruojun.dataanalysisagent.agent.state.MainGraphState;
import com.zhouruojun.dataanalysisagent.config.ParallelExecutionConfig;
import com.zhouruojun.dataanalysisagent.service.OllamaService;
import com.zhouruojun.dataanalysisagent.tools.DataAnalysisToolCollection;
import dev.langchain4j.data.message.*;
import dev.langchain4j.data.text.TextFile;
import dev.langchain4j.internal.ValidationUtils;
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
import com.zhouruojun.a2acore.spec.FileContent;
import dev.langchain4j.agent.tool.ToolSpecification;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.annotation.PostConstruct;

/**
 * 数据分析智能体控制器核心类
 * 支持LangGraph4j图结构和数据分析工作流
 */
@Slf4j
@Component
public class DataAnalysisControllerCore {

    // LangGraph相关组件
    private Map<String, CompiledGraph<MainGraphState>> compiledGraphCache;
    private Map<String, StateGraph<MainGraphState>> stateGraphCache;
    private BaseCheckpointSaver checkpointSaver;
    
    // 核心依赖
    @Autowired
    private OllamaService ollamaService;
    
    
    @Autowired
    private ChatLanguageModel chatLanguageModel;
    
    // 数据分析工具集合
    @Autowired
    private DataAnalysisToolCollection toolCollection;
    
    // 并行执行配置
    @Autowired
    private ParallelExecutionConfig parallelExecutionConfig;
    
    // 会话管理
    private final Map<String, List<ChatMessage>> sessionHistory = new ConcurrentHashMap<>();
    private final Map<String, Object> sessionCache = new ConcurrentHashMap<>();
    private final Map<String, Long> sessionAccessTimes = new ConcurrentHashMap<>();
    private static final long SESSION_TTL_MS = Duration.ofMinutes(45).toMillis();
    private volatile long lastCleanupTimestamp = 0L;
    private final Object cleanupLock = new Object();

    @PostConstruct
    public void init() throws GraphStateException {
        log.info("Initializing DataAnalysisControllerCore with LangGraph4j support");
        
        // 初始化缓存
        compiledGraphCache = new ConcurrentHashMap<>();
        stateGraphCache = new ConcurrentHashMap<>();
        
        // 初始化checkpoint saver
        checkpointSaver = new MemorySaver();
        
        log.info("DataAnalysisControllerCore initialized successfully");
    }

    public String processStreamReturnStr(AgentChatRequest request) {
        try {
            log.info("Processing data analysis request with LangGraph: {}", JSONObject.toJSON(request));
            
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
                log.info("Graph node output: {}", output.node());
                
                MainGraphState state = output.state();
                
                // 获取分析结果 - 修复：优先获取finalResponse，但不要被后续节点覆盖
                if (state.value("finalResponse").isPresent()) {
                    String currentResponse = (String) state.value("finalResponse").get();
                    // 只有当当前响应不为空且不是默认值时，才更新finalResponse
                    if (!currentResponse.isEmpty() && !currentResponse.equals("进行中")) {
                        finalResponse = currentResponse;
                    }
                } else if (state.value("analysisResult").isPresent()) {
                    finalResponse = (String) state.value("analysisResult").get();
                } else if (state.value("errorMessage").isPresent()) {
                    finalResponse = "错误: " + state.value("errorMessage").get();
                } else if (!state.messages().isEmpty()) {
                    // 获取最后一条AI消息作为响应
                    var lastMessage = state.lastMessage();
                    if (lastMessage.isPresent()) {
                        var message = lastMessage.get();
                        if (message instanceof dev.langchain4j.data.message.AiMessage aiMessage) {
                            finalResponse = aiMessage.text();
                        }
                    }
                }
                
                // 构建节点执行信息
                String nodeStatus = "进行中";
                if (state.value("errorMessage").isPresent()) {
                    nodeStatus = "错误: " + state.value("errorMessage").get();
                } else if (state.value("finalResponse").isPresent()) {
                    nodeStatus = "完成";
                } else if (state.getSubgraphResults().isPresent() && !state.getSubgraphResults().get().isEmpty()) {
                    nodeStatus = "子图结果已准备";
                }
                
                resultBuilder.append("节点: ").append(output.node())
                           .append(" -> ").append(nodeStatus).append("\n");
            }
            
            // 构建最终响应
            String formattedResponse = String.format("""
                🔬 数据分析智能体回复:
                
                %s
                
                🔄 执行流程:
                %s
                """, 
                finalResponse,
                    resultBuilder
            );
            
            // 缓存会话信息
            sessionCache.put(sessionId, Map.of(
                "chat", chat,
                "aiResponse", finalResponse,
                "response", formattedResponse,
                "timestamp", System.currentTimeMillis(),
                "executionTrace", resultBuilder.toString()
            ));
            
            return formattedResponse;
            
        } catch (Exception e) {
            log.error("Error processing data analysis request with graph", e);
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
     * 构建数据分析图
     */
    private StateGraph<MainGraphState> buildGraph(String username, String requestId) 
            throws GraphStateException {
        
        return new DataAnalysisGraphBuilder()
                .chatLanguageModel(chatLanguageModel)
                .toolCollection(toolCollection)
                .parallelExecutionConfig(parallelExecutionConfig)
                .username(username)
                .requestId(requestId)
                .build();
    }
    
    /**
     * 构建编译配置
     */
    private CompileConfig buildCompileConfig() {
        return CompileConfig.builder()
                .checkpointSaver(checkpointSaver)
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
     * 检查Ollama状态
     */
    public String checkOllamaStatus() {
        try {
            if (ollamaService.isAvailable()) {
                return "✅ Ollama服务正常运行";
            } else {
                return "❌ Ollama服务不可用";
            }
        } catch (Exception e) {
            log.error("Error checking Ollama status", e);
            return "检查Ollama状态失败: " + e.getMessage();
        }
    }

    /**
     * 获取可用模型
     */
    public String getAvailableModels() {
        try {
            return ollamaService.getAvailableModels();
        } catch (Exception e) {
            log.error("Error getting available models", e);
            return "获取模型列表失败: " + e.getMessage();
        }
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
     * 重置分析流程
     */
    public void resetAnalysisFlow(String sessionId) {
        log.info("Resetting analysis flow for session: {}", sessionId);
        compiledGraphCache.remove(sessionId);
        stateGraphCache.remove(sessionId);
        
        // 清理检查点
        if (checkpointSaver != null) {
            // MemorySaver 会自动清理，无需额外操作
        }
    }

    /**
     * 清理所有图缓存
     */
    public void clearAllGraphCache() {
        stateGraphCache.clear();
        compiledGraphCache.clear();
        log.info("Cleared all graph caches");
    }

    /**
     * 获取活跃会话数量
     */
    public int getActiveSessionCount() {
        return compiledGraphCache.size();
    }

    /**
     * 处理A2A流式任务
     */
    public void processStreamWithA2a(String requestId,
                                     String sessionId,
                                     Message message,
                                     List<ToolSpecification> userTools,
                                     String userEmail) {
        updateSessionAccess(sessionId);
        cleanupExpiredSessionsIfNeeded();
        CompletableFuture.runAsync(() -> {
            try {
                processStreamA2a(requestId, sessionId, message, userTools, userEmail);
            } catch (Exception e) {
                log.error("processStreamWithA2a error", e);
                throw new RuntimeException(e);
            }
            // 处理流式消息（现在不需要发送事件，因为使用同步处理）
            log.info("A2A stream processing completed for requestId: {}", requestId);
        });
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
                log.info("First call to data analysis agent for session: {}", sessionId);
                return processFirstA2aCall(sessionId, userMessage);
            } else {
                // 情况2：后续调用 - 从cache获取图状态并继续执行
                log.info("Resume call to data analysis agent for session: {}", sessionId);
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
            // 初始化状态图和编译图
            StateGraph<MainGraphState> stateGraph = buildGraphWithSkill(
                    Collections.emptyList(), "a2a-user", sessionId, null);
            stateGraphCache.put(sessionId, stateGraph);
            
            CompiledGraph<MainGraphState> graph = stateGraph.compile(buildCompileConfig());
            compiledGraphCache.put(sessionId, graph);
            
            // 执行图
            RunnableConfig runnableConfig = RunnableConfig.builder()
                    .threadId(sessionId)
                    .build();
            
            List<Content> contents = Collections.singletonList(TextContent.from(userMessage));
            UserMessage userMsg = UserMessage.from(contents);
            
            // 创建初始状态，确保messages字段正确设置
            Map<String, Object> initialState = new HashMap<>();
            initialState.put("originalUserQuery", userMessage);
            initialState.put("sessionId", sessionId);
            initialState.put("requestId", sessionId);
            initialState.put("username", "a2a-user");
            
            AsyncGenerator<NodeOutput<MainGraphState>> stream = graph.stream(initialState, runnableConfig);
            
            // 收集所有输出，但只返回最终结果
            StringBuilder result = new StringBuilder();
            String finalResult = null;
            
            for (NodeOutput<MainGraphState> output : stream) {
                String nodeName = output.node();
                MainGraphState state = output.state();

                // 只收集summary节点的输出作为最终结果
                if ("summary".equals(nodeName) && state.lastMessage().isPresent()) {
                    ChatMessage lastMsg = state.lastMessage().get();
                    if (lastMsg instanceof AiMessage) {
                        finalResult = ((AiMessage) lastMsg).text();
                        log.info("Summary node output: {}", finalResult);
                    }
                }
                
                // 记录所有节点的输出用于调试
                if (state.lastMessage().isPresent()) {
                    ChatMessage lastMsg = state.lastMessage().get();
                    if (lastMsg instanceof AiMessage) {
                        result.append("[").append(nodeName).append("] ").append(((AiMessage) lastMsg).text()).append("\n");
                    }
                }
            }
            
            // 返回summary节点的结果，如果没有则返回调试信息
            if (finalResult != null && !finalResult.trim().isEmpty()) {
                return finalResult;
            } else {
                log.warn("No summary result found, returning debug info: {}", result);
                return "数据分析流程执行完成，但未获得最终总结结果。调试信息:\n" + result;
            }
            
        } catch (Exception e) {
            log.error("Error processing first A2A call: sessionId={}", sessionId, e);
            return "Error: 第一次A2A调用处理失败: " + e.getMessage();
        }
    }
    
    /**
     * 处理后续A2A调用（resume）
     */
    private String processResumeA2aCall(String sessionId, String userMessage) {
        try {
            // 从cache获取编译图
            CompiledGraph<MainGraphState> graph = compiledGraphCache.get(sessionId);
            if (graph == null) {
                log.warn("No compiled graph found for session: {}, treating as first call", sessionId);
                return processFirstA2aCall(sessionId, userMessage);
            }
            
            // 获取当前状态
            RunnableConfig runnableConfig = RunnableConfig.builder()
                    .threadId(sessionId)
                    .build();
            
            StateSnapshot<MainGraphState> state = graph.getState(runnableConfig);
            
            // 将新消息添加到状态中并继续执行
            List<Content> contents = Collections.singletonList(TextContent.from(userMessage));
            UserMessage userMsg = UserMessage.from(contents);
            
            // 创建状态更新，确保messages字段正确设置
            Map<String, Object> newMessages = new HashMap<>();
            newMessages.put("messages", List.of(userMsg));
            
            // 更新状态并从当前状态继续执行
            RunnableConfig newConfig = graph.updateState(state.config(), newMessages);
            AsyncGenerator<NodeOutput<MainGraphState>> stream = graph.stream(null, newConfig);
            
            // 收集所有输出
            StringBuilder result = new StringBuilder();
            for (NodeOutput<MainGraphState> output : stream) {
                MainGraphState stateOutput = output.state();
                if (stateOutput.lastMessage().isPresent()) {
                    ChatMessage lastMsg = stateOutput.lastMessage().get();
                    if (lastMsg instanceof dev.langchain4j.data.message.AiMessage) {
                        result.append(((dev.langchain4j.data.message.AiMessage) lastMsg).text()).append("\n");
                    }
                }
            }
            
            return result.length() > 0 ? result.toString().trim() : "数据分析完成: " + userMessage;
            
        } catch (Exception e) {
            log.error("Error processing resume A2A call: sessionId={}", sessionId, e);
            return "Error: Resume A2A调用处理失败: " + e.getMessage();
        }
    }

    /**
     * 处理A2A流式任务的核心逻辑
     */
    private AsyncGenerator<NodeOutput<MainGraphState>> processStreamA2a(String requestId,
                                                                           String sessionId,
                                                                           Message message,
                                                                           List<ToolSpecification> userTools,
                                                                           String userEmail) {
        updateSessionAccess(sessionId);
        cleanupExpiredSessionsIfNeeded();
        try {
            Map<String, Object> messageMetadata = message.getMetadata();
            Object skillObj = messageMetadata.get("skill");
            String skill = null;
            if (skillObj != null) {
                skill = skillObj.toString();
            }
            
            StateGraph<MainGraphState> stateGraph = stateGraphCache.getOrDefault(requestId,
                    buildGraphWithSkill(userTools, userEmail, requestId, skill));
            stateGraphCache.put(requestId, stateGraph);

            CompiledGraph<MainGraphState> graph = compiledGraphCache.getOrDefault(requestId,
                    stateGraph.compile(buildCompileConfig()));
            compiledGraphCache.put(requestId, graph);

            RunnableConfig runnableConfig = RunnableConfig.builder()
                    .threadId(requestId)
                    .build();

            List<Part> parts = message.getParts();
            List<Content> contents = parts.stream().map(part -> {
                if (part instanceof FilePart filePart) {
                    FileContent file = filePart.getFile();
                    TextFile base64data = TextFile.builder()
                            .url(file.getUri())
                            .base64Data(ValidationUtils.ensureNotBlank(file.getBytes(), "base64data"))
                            .mimeType(file.getMimeType())
                            .build();
                    return TextFileContent.from(base64data);
                } else if (part instanceof DataPart dataPart) {
                    return TextContent.from(JSONObject.toJSONString(dataPart.getData()));
                } else if (part instanceof TextPart textPart) {
                    return TextContent.from(textPart.getText());
                } else {
                    return TextContent.from(JSONObject.toJSONString(part.getMetadata()));
                }
            }).collect(Collectors.toList());
            
            return graph.stream(Map.of(
                    "messages", UserMessage.from(contents),
                    "sessionId", sessionId,
                    "requestId", requestId,
                    "username", userEmail
                    ), runnableConfig);
        } catch (Exception e) {
            log.error("processStreamA2a error", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * 构建带技能的数据分析图
     */
    private StateGraph<MainGraphState> buildGraphWithSkill(List<ToolSpecification> userTools, 
                                                              String userEmail, 
                                                              String requestId, 
                                                              String skill) 
            throws GraphStateException {
        
        return new DataAnalysisGraphBuilder()
                .chatLanguageModel(chatLanguageModel)
                .toolCollection(toolCollection)
                .parallelExecutionConfig(parallelExecutionConfig)
                .username(userEmail)
                .requestId(requestId)
                .build();
    }






    /**
     * 统一的resume流处理 - 从cache中获取图状态并继续执行
     */
    public void resumeStream(String requestId, Message message, String userEmail) {
        updateSessionAccess(requestId);
        cleanupExpiredSessionsIfNeeded();
        CompletableFuture.runAsync(() -> {
            try {
                log.info("Resuming stream for requestId: {}, user: {}", requestId, userEmail);
                
                RunnableConfig runnableConfig = RunnableConfig.builder()
                        .threadId(requestId)
                        .build();
                
                // 从cache中获取编译好的图
                CompiledGraph<MainGraphState> graph = compiledGraphCache.get(requestId);
                if (graph == null) {
                    log.error("No compiled graph found for requestId: {}", requestId);
                    return;
                }
                
                // 获取当前状态
                StateSnapshot<MainGraphState> state = graph.getState(runnableConfig);
                
                // 将新消息添加到状态中并继续执行
                List<Content> contents = message.getParts().stream().map(part -> {
                    if (part instanceof FilePart filePart) {
                        FileContent file = filePart.getFile();
                        TextFile base64data = TextFile.builder()
                                .url(file.getUri())
                                .base64Data(ValidationUtils.ensureNotBlank(file.getBytes(), "base64data"))
                                .mimeType(file.getMimeType())
                                .build();
                        return TextFileContent.from(base64data);
                    } else if (part instanceof DataPart dataPart) {
                        return TextContent.from(JSONObject.toJSONString(dataPart.getData()));
                    } else if (part instanceof TextPart textPart) {
                        return TextContent.from(textPart.getText());
                    } else {
                        return TextContent.from(JSONObject.toJSONString(part.getMetadata()));
                    }
                }).collect(Collectors.toList());
                
                UserMessage userMsg = UserMessage.from(contents);
                Map<String, Object> newMessages = new HashMap<>();
                newMessages.put("messages", List.of(userMsg));
                
                // 更新状态并从当前状态继续执行
                RunnableConfig newConfig = graph.updateState(state.config(), newMessages);
                graph.stream(null, newConfig);
                
                // 处理流式消息（现在不需要发送事件，因为使用同步处理）
                log.info("A2A resume stream processing completed for requestId: {}", requestId);
                
            } catch (Exception e) {
                log.error("resumeStream error for requestId: {}", requestId, e);
                throw new RuntimeException(e);
            }
            
            // 处理流式消息（现在不需要发送事件，因为使用同步处理）
            log.info("A2A stream processing completed for requestId: {}", requestId);
        });
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
                log.info("Removed expired data-analysis session {}", expiredSessionId);
                return true;
            }
            return false;
        });
    }
}
