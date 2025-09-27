package com.zhouruojun.dataanalysisagent.agent.actions;

import cn.hutool.core.collection.CollectionUtil;
import com.zhouruojun.dataanalysisagent.agent.BaseAgent;
import com.zhouruojun.dataanalysisagent.agent.state.BaseAgentState;
import com.zhouruojun.dataanalysisagent.config.ParallelExecutionConfig;
import com.zhouruojun.dataanalysisagent.tools.DataAnalysisToolCollection;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 通用工具执行节点
 * 支持主智能体和子智能体的工具执行
 * 支持并行和串行两种执行模式
 */
@Slf4j
public class ExecuteTools<T extends BaseAgentState> implements NodeAction<T> {

    /**
     * 智能体实例
     */
    final BaseAgent agent;

    /**
     * 智能体名称
     */
    final String agentName;

    /**
     * 工具集合管理器
     */
    private DataAnalysisToolCollection toolCollection;
    
    /**
     * 并行执行配置
     */
    private ParallelExecutionConfig parallelConfig;
    
    /**
     * 构造函数
     */
    public ExecuteTools(String agentName, BaseAgent agent, DataAnalysisToolCollection toolCollection, ParallelExecutionConfig parallelConfig) {
        this.agentName = agentName;
        this.agent = agent;
        this.toolCollection = toolCollection;
        this.parallelConfig = parallelConfig;
    }
    
    /**
     * 线程池执行器，用于并行执行工具
     */
    private static ExecutorService executorService;
    
    /**
     * 初始化线程池
     */
    static {
        initializeExecutorService();
    }
    
    /**
     * 初始化线程池
     */
    private static void initializeExecutorService() {
        if (executorService == null) {
            executorService = new ThreadPoolExecutor(
                4, // 核心线程数
                8, // 最大线程数
                60L, TimeUnit.SECONDS, // 空闲时间
                new LinkedBlockingQueue<>(100), // 队列容量
                r -> {
                    Thread thread = new Thread(r);
                    thread.setName("tool-executor-" + System.nanoTime());
                    thread.setDaemon(true);
                    return thread;
                }
            );
        }
    }

    /**
     * 构造函数
     *
     * @param agentName 智能体名称
     * @param agent 智能体实例
     * @param toolCollection 工具集合管理器
     */
    public ExecuteTools(@NonNull String agentName,
                       @NonNull BaseAgent agent,
                       @NonNull DataAnalysisToolCollection toolCollection) {
        this.agentName = agentName;
        this.agent = agent;
        this.toolCollection = toolCollection;
    }

    /**
     * 执行工具调用逻辑
     *
     * @param state 当前智能体状态
     * @return 包含执行结果的映射
     */
    @Override
    public Map<String, Object> apply(T state) {

        List<ToolExecutionRequest> toolExecutionRequests = null;
        Optional<ChatMessage> chatMessage = state.lastMessage();
        
        if (ChatMessageType.AI == chatMessage.get().type()) {
            toolExecutionRequests = state.lastMessage()
                    .filter(m -> ChatMessageType.AI == m.type())
                    .map(m -> (AiMessage) m)
                    .filter(AiMessage::hasToolExecutionRequests)
                    .map(AiMessage::toolExecutionRequests)
                    .orElseThrow(() -> new IllegalArgumentException("no tool execution request found!"));
        } else {
            List<ChatMessage> messages = state.messages();
            int aiCount = 0;
            for (int i = messages.size() - 1; i > 0; i--) {
                if (aiCount > 0) {
                    break;
                }
                if (ChatMessageType.AI == messages.get(i).type()) {
                    aiCount++;
                    AiMessage aiMessage = (AiMessage) messages.get(i);
                    if (aiMessage.hasToolExecutionRequests()) {
                        toolExecutionRequests = aiMessage.toolExecutionRequests();
                    }
                }
            }
        }

        if (toolExecutionRequests == null || CollectionUtil.isEmpty(toolExecutionRequests)) {
            throw new IllegalArgumentException("no tool execution request found!");
        }

        // 执行工具调用 - 支持并行执行
        List<ToolExecutionResultMessage> result;
        
        // 检查是否应该使用并行执行
        boolean shouldUseParallel = shouldUseParallelExecution(toolExecutionRequests);
        
        if (shouldUseParallel) {
            // 使用并行执行
            log.info("并行执行 {} 个工具", toolExecutionRequests.size());
            result = executeToolsInParallel(toolExecutionRequests, state);
        } else {
            // 使用串行执行
            log.info("串行执行 {} 个工具", toolExecutionRequests.size());
            result = executeToolsSequentially(toolExecutionRequests, state);
        }

        if (result.isEmpty()) {
            throw new IllegalArgumentException("no tool execution result found!");
        }

        // 记录工具执行完成
        log.info("工具执行完成，共执行了 {} 个工具", result.size());

        // 检查是否有停止信号
        if ("STOP".equals(result.get(0).text())) {
            return Map.of("next", "FINISH", "messages", result);
        }

        // 将工具执行结果添加到状态中，这样智能体才能看到工具执行的结果
        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("next", "callback");
        resultMap.put("messages", result);
        
        // 检查状态是否有更新（如TodoList等）
        // 因为框架需要知道状态的存在，以便正确管理状态
        if (state.getOriginalUserQuery().isPresent()) {
            resultMap.put("originalUserQuery", state.getOriginalUserQuery().get());
        }
        
        // 只有MainGraphState才有这些方法，需要类型检查
        if (state instanceof com.zhouruojun.dataanalysisagent.agent.state.MainGraphState) {
            com.zhouruojun.dataanalysisagent.agent.state.MainGraphState mainState = 
                (com.zhouruojun.dataanalysisagent.agent.state.MainGraphState) state;
            if (mainState.getTodoList().isPresent()) {
                resultMap.put("todoList", mainState.getTodoList().get());
            }
            if (mainState.getSubgraphResults().isPresent()) {
                resultMap.put("subgraphResults", mainState.getSubgraphResults().get());
            }
        }
        
        return resultMap;
    }


    /**
     * 判断是否应该使用并行执行
     *
     * @param requests 工具执行请求列表
     * @return 是否应该使用并行执行
     */
    private boolean shouldUseParallelExecution(List<ToolExecutionRequest> requests) {
        // 如果配置禁用并行执行，则使用串行
        if (parallelConfig == null || !parallelConfig.isEnabled()) {
            return false;
        }
        
        // 如果工具数量小于最小阈值，使用串行
        if (requests.size() < parallelConfig.getMinToolsForParallel()) {
            return false;
        }
        
        // 检查工具之间是否有依赖关系
        // 这里可以根据工具名称判断是否有依赖关系
        return !hasToolDependencies(requests);
    }
    
    /**
     * 检查工具之间是否有依赖关系
     *
     * @param requests 工具执行请求列表
     * @return 是否有依赖关系
     */
    private boolean hasToolDependencies(List<ToolExecutionRequest> requests) {
        // 可以根据实际需求扩展更复杂的依赖关系判断
        
//        for (ToolExecutionRequest request : requests) {
//            String toolName = request.name();
//
//            // 如果包含数据加载工具，通常需要先执行
//            if (toolName.contains("load") || toolName.contains("Load")) {
//                return true;
//            }
//
//            // 如果包含分析工具，通常需要先有数据
//            if (toolName.contains("calculate") || toolName.contains("analyze")) {
//                return true;
//            }
//        }
        
        return false;
    }

    /**
     * 串行执行工具调用
     *
     * @param requests 工具执行请求列表
     * @return 工具执行结果列表
     */
    private List<ToolExecutionResultMessage> executeToolsSequentially(List<ToolExecutionRequest> requests, T state) {
        return requests.stream()
                .map(request -> executeTool(request, state))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }
    
    /**
     * 并行执行工具调用
     *
     * @param requests 工具执行请求列表
     * @return 工具执行结果列表
     */
    private List<ToolExecutionResultMessage> executeToolsInParallel(List<ToolExecutionRequest> requests, T state) {
        try {
            // 创建并行任务
            List<CompletableFuture<Optional<ToolExecutionResultMessage>>> futures = requests.stream()
                    .map(request -> CompletableFuture.supplyAsync(() -> {
                        log.info("开始并行执行工具: {}", request.name());
                        return executeTool(request, state);
                    }, executorService))
                    .collect(Collectors.toList());
            
            // 等待所有任务完成
            CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                    futures.toArray(new CompletableFuture[0])
            );
            
            // 设置超时时间
            int timeoutSeconds = parallelConfig != null ? parallelConfig.getTimeoutSeconds() : 30;
            allFutures.get(timeoutSeconds, TimeUnit.SECONDS);
            
            // 收集结果
            List<ToolExecutionResultMessage> results = futures.stream()
                    .map(CompletableFuture::join)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .collect(Collectors.toList());
            
            log.info("并行执行完成，成功执行 {} 个工具", results.size());
            return results;
            
        } catch (Exception e) {
            log.error("并行执行工具时发生错误: {}", e.getMessage(), e);
            
            // 如果并行执行失败，回退到串行执行
            log.warn("回退到串行执行模式");
            return executeToolsSequentially(requests, state);
        }
    }

    /**
     * 执行单个工具调用
     *
     * @param request 工具执行请求
     * @return 工具执行结果
     */
    private Optional<ToolExecutionResultMessage> executeTool(ToolExecutionRequest request, T state) {
        try {
            long startTime = System.currentTimeMillis();
            log.info("执行工具: {} (线程: {})", request.name(), Thread.currentThread().getName());
            
            // 使用工具集合执行工具
            String result = toolCollection.executeTool(request, state);
            
            long duration = System.currentTimeMillis() - startTime;
            log.info("工具 {} 执行完成，耗时: {}ms", request.name(), duration);
            
            return Optional.of(new ToolExecutionResultMessage(request.id(), request.name(), result));
            
        } catch (Exception e) {
            log.error("工具 {} 执行失败: {}", request.name(), e.getMessage(), e);
            String errorResult = "Error executing tool: " + e.getMessage();
            return Optional.of(new ToolExecutionResultMessage(request.id(), request.name(), errorResult));
        }
    }
    
    /**
     * 关闭线程池（应用关闭时调用）
     */
    public static void shutdown() {
        if (executorService != null && !executorService.isShutdown()) {
            log.info("正在关闭工具执行线程池...");
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                    log.warn("线程池未能在10秒内正常关闭，强制关闭");
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                log.error("等待线程池关闭时被中断", e);
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
            log.info("工具执行线程池已关闭");
        }
    }
}