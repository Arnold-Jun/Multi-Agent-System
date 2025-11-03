package com.zhouruojun.travelingagent.agent.actions;

import com.zhouruojun.travelingagent.agent.BaseAgent;
import com.zhouruojun.travelingagent.agent.state.BaseAgentState;
import com.zhouruojun.travelingagent.agent.state.MainGraphState;
import com.zhouruojun.travelingagent.agent.state.SubgraphState;
import com.zhouruojun.travelingagent.agent.state.subgraph.ToolExecutionHistory;
import com.zhouruojun.travelingagent.config.ParallelExecutionConfig;
import com.zhouruojun.travelingagent.mcp.TravelingToolProviderManager;
import com.zhouruojun.travelingagent.tools.extractor.ToolExtractionManager;
import lombok.extern.slf4j.Slf4j;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
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
 * 
 * 工具结果的提取和处理由ToolExtractionManager统一管理
 * 包括JSON-RPC格式解析和自定义提取器的应用
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
     * 工具提供者管理器
     */
    private final TravelingToolProviderManager toolProviderManager;
    
    /**
     * 并行执行配置
     */
    private ParallelExecutionConfig parallelConfig;
    
    /**
     * 构造函数
     */
    public ExecuteTools(String agentName, BaseAgent agent, TravelingToolProviderManager toolProviderManager, ParallelExecutionConfig parallelConfig) {
        this.agentName = agentName;
        this.agent = agent;
        this.toolProviderManager = toolProviderManager;
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
     * 执行工具调用逻辑
     *
     * @param state 当前智能体状态
     * @return 包含执行结果的映射
     */
    @Override
    public Map<String, Object> apply(T state) {
        // 获取工具执行请求
        List<ToolExecutionRequest> toolExecutionRequests = extractToolExecutionRequests(state);

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

        // 将工具执行结果添加到状态中
        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("next", "callback");
        resultMap.put("messages", result);
        
        // 获取工具执行历史管理对象并记录执行结果（包含参数信息）
        ToolExecutionHistory executionHistory = getToolExecutionHistoryFromState(state);
        recordToolExecutions(toolExecutionRequests, result, executionHistory);
        
        // 格式化为LLM可读的字符串（只显示最近20条，避免上下文过长）
        //TODO:工具执行记忆管理待实现！
        String formattedHistory = executionHistory.toFormattedString(20);
        
        // 保存历史对象和格式化字符串
        resultMap.put("toolExecutionHistory", executionHistory);
        resultMap.put("toolExecutionResult", formattedHistory);
        
        // 记录统计信息
        log.info("工具执行历史统计: {}", executionHistory.getStatistics());
        
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
//            // 如果包含简历解析工具，通常需要先执行
//            if (toolName.contains("parse") || toolName.contains("Parse")) {
//                return true;
//            }
//
//            // 如果包含匹配度分析工具，通常需要先有简历和岗位信息
//            if (toolName.contains("match") || toolName.contains("Match")) {
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
            
            // 使用工具提供者管理器执行工具
            String rawResult = toolProviderManager.executeTool(getRequestId(state), request);
            
            // 使用ToolExtractionManager处理工具结果（包括JSON-RPC提取和自定义提取器）
            ToolExtractionManager extractionManager = toolProviderManager.getToolExtractionManager();
            String processedResult = extractionManager.processToolResult(request.name(), rawResult);
            
            long duration = System.currentTimeMillis() - startTime;
            log.info("工具 {} 执行完成，耗时: {}ms", request.name(), duration);
            
            return Optional.of(new ToolExecutionResultMessage(request.id(), request.name(), processedResult));
            
        } catch (Exception e) {
            log.error("工具 {} 执行失败: {}", request.name(), e.getMessage(), e);
            String errorResult = "Error executing tool: " + e.getMessage();
            // 返回错误结果而不是抛出异常，让流程继续
            return Optional.of(new ToolExecutionResultMessage(request.id(), request.name(), errorResult));
        }
    }
    
    /**
     * 记录工具执行历史（包含参数信息）
     * 
     * @param requests 工具执行请求列表
     * @param results 工具执行结果列表
     * @param executionHistory 工具执行历史对象
     */
    private void recordToolExecutions(List<ToolExecutionRequest> requests, 
                                      List<ToolExecutionResultMessage> results,
                                      ToolExecutionHistory executionHistory) {
        // 创建请求映射（通过ID和工具名索引，支持多种匹配方式）
        Map<String, ToolExecutionRequest> requestMapById = new HashMap<>();
        Map<String, List<ToolExecutionRequest>> requestMapByName = new HashMap<>();
        for (ToolExecutionRequest request : requests) {
            if (request.id() != null && !request.id().isEmpty()) {
                requestMapById.put(request.id(), request);
            }
            requestMapByName.computeIfAbsent(request.name(), k -> new java.util.ArrayList<>()).add(request);
        }
        
        // 添加当前批次的工具执行记录（包含参数信息）
        int resultIndex = 0;
        for (ToolExecutionResultMessage resultMsg : results) {
            ToolExecutionRequest matchingRequest = findMatchingRequest(
                resultMsg, resultIndex, requests, requestMapById, requestMapByName);
            
            String arguments = extractArguments(matchingRequest, resultMsg.toolName());
            executionHistory.addRecord(resultMsg.toolName(), arguments, resultMsg.text(), 0);
            resultIndex++;
        }
    }
    
    /**
     * 查找匹配的工具执行请求
     * 
     * @param resultMsg 工具执行结果消息
     * @param resultIndex 结果索引
     * @param requests 工具执行请求列表
     * @param requestMapById 按ID索引的请求映射
     * @param requestMapByName 按工具名索引的请求映射
     * @return 匹配的请求，如果未找到则返回null
     */
    private ToolExecutionRequest findMatchingRequest(ToolExecutionResultMessage resultMsg,
                                                     int resultIndex,
                                                     List<ToolExecutionRequest> requests,
                                                     Map<String, ToolExecutionRequest> requestMapById,
                                                     Map<String, List<ToolExecutionRequest>> requestMapByName) {
        // 首先尝试通过ID匹配
        try {
            String resultId = resultMsg.id();
            if (resultId != null && !resultId.isEmpty()) {
                ToolExecutionRequest matched = requestMapById.get(resultId);
                if (matched != null) {
                    return matched;
                }
            }
        } catch (Exception e) {
            log.debug("无法获取结果消息ID，尝试其他匹配方式: {}", e.getMessage());
        }
        
        // 如果ID匹配失败，尝试通过工具名和位置匹配
        if (resultIndex < requests.size()) {
            ToolExecutionRequest candidate = requests.get(resultIndex);
            if (candidate.name().equals(resultMsg.toolName())) {
                return candidate;
            }
        }
        
        // 如果仍然匹配失败，尝试通过工具名匹配
        List<ToolExecutionRequest> requestsWithSameName = requestMapByName.get(resultMsg.toolName());
        if (requestsWithSameName != null && !requestsWithSameName.isEmpty()) {
            return requestsWithSameName.get(0);
        }
        
        return null;
    }
    
    /**
     * 从工具执行请求中提取参数字符串
     * 
     * @param request 工具执行请求
     * @param toolName 工具名称（用于日志）
     * @return 参数字符串，如果无法提取则返回null
     */
    private String extractArguments(ToolExecutionRequest request, String toolName) {
        if (request == null) {
            log.warn("无法为工具 {} 找到匹配的请求，参数信息将丢失", toolName);
            return null;
        }
        
        Object argsObj = request.arguments();
        if (argsObj == null) {
            return null;
        }
        
        // 将参数对象转换为字符串
        if (argsObj instanceof String) {
            return (String) argsObj;
        }
        
        // 如果是Map或其他对象，转换为JSON字符串
        try {
            return com.alibaba.fastjson.JSONObject.toJSONString(argsObj);
        } catch (Exception e) {
            log.debug("参数转换失败，使用toString: {}", e.getMessage());
            return String.valueOf(argsObj);
        }
    }
    
    /**
     * 从状态中提取工具执行请求
     * 
     * @param state 当前状态
     * @return 工具执行请求列表
     * @throws IllegalArgumentException 如果没有找到工具执行请求
     */
    private List<ToolExecutionRequest> extractToolExecutionRequests(T state) {
        Optional<ChatMessage> chatMessage = state.lastMessage();
        
        if (chatMessage.isPresent() && ChatMessageType.AI == chatMessage.get().type()) {
            // 从最后一条AI消息中获取工具执行请求
            return state.lastMessage()
                    .filter(m -> ChatMessageType.AI == m.type())
                    .map(m -> (AiMessage) m)
                    .filter(AiMessage::hasToolExecutionRequests)
                    .map(AiMessage::toolExecutionRequests)
                    .orElseThrow(() -> new IllegalArgumentException("最后一条AI消息中没有工具执行请求"));
        } else {
            // 从消息历史中查找最近的AI消息
            List<ChatMessage> messages = state.messages();
            for (int i = messages.size() - 1; i >= 0; i--) {
                ChatMessage message = messages.get(i);
                if (ChatMessageType.AI == message.type()) {
                    AiMessage aiMessage = (AiMessage) message;
                    if (aiMessage.hasToolExecutionRequests()) {
                        return aiMessage.toolExecutionRequests();
                    }
                }
            }
        }
        
        throw new IllegalArgumentException("没有找到工具执行请求");
    }
    
    /**
     * 从状态中获取工具执行历史对象
     * 
     * @param state 当前状态
     * @return 工具执行历史对象
     * @throws IllegalStateException 
     */
    private ToolExecutionHistory getToolExecutionHistoryFromState(T state) {
        if (state instanceof MainGraphState) {
            return ((MainGraphState) state).getToolExecutionHistory();
        } else if (state instanceof SubgraphState) {
            return ((SubgraphState) state).getToolExecutionHistory();
        }
        throw new IllegalStateException(
            String.format("期望MainGraphState或SubgraphState，但实际类型为: %s", state.getClass().getName())
        );
    }

    /**
     * 获取请求ID
     */
    private String getRequestId(T state) {
        // 简化版本，直接返回默认值
        return "default";
    }

}