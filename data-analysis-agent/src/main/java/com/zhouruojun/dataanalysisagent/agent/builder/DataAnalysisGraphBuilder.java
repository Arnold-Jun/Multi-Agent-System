package com.zhouruojun.dataanalysisagent.agent.builder;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

import com.zhouruojun.dataanalysisagent.agent.BaseAgent;
import com.zhouruojun.dataanalysisagent.agent.actions.CallAgent;
import com.zhouruojun.dataanalysisagent.agent.actions.TodoListParser;
import com.zhouruojun.dataanalysisagent.config.ParallelExecutionConfig;
import com.zhouruojun.dataanalysisagent.agent.builder.subgraph.StatisticalAnalysisSubgraphBuilder;
import com.zhouruojun.dataanalysisagent.agent.builder.subgraph.DataVisualizationSubgraphBuilder;
import com.zhouruojun.dataanalysisagent.agent.builder.subgraph.WebSearchSubgraphBuilder;
import com.zhouruojun.dataanalysisagent.agent.builder.subgraph.ComprehensiveAnalysisSubgraphBuilder;
import com.zhouruojun.dataanalysisagent.agent.serializers.AgentSerializers;
import com.zhouruojun.dataanalysisagent.agent.state.MainGraphState;
import com.zhouruojun.dataanalysisagent.agent.todo.TodoTask;
import com.zhouruojun.dataanalysisagent.agent.todo.TodoList;
import com.zhouruojun.dataanalysisagent.common.PromptTemplateManager;
import com.zhouruojun.dataanalysisagent.tools.DataAnalysisToolCollection;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;

import java.util.*;

import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.bsc.async.AsyncGenerator;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.EdgeAction;
import org.bsc.langgraph4j.serializer.StateSerializer;
import org.bsc.langgraph4j.streaming.StreamingOutput;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * 数据分析智能体图构建器
 * 用于构建数据分析智能体的执行图，支持子图执行
 */
@Slf4j
public class DataAnalysisGraphBuilder {
    
    // 智能体名称到子图名称的映射
    private static final Map<String, String> AGENT_TO_SUBGRAPH_MAP = Map.of(
        "statisticalAnalysisAgent", "statistical_analysis_subgraph",
        "dataVisualizationAgent", "data_visualization_subgraph", 
        "webSearchAgent", "web_search_subgraph",
        "comprehensiveAnalysisAgent", "comprehensive_analysis_subgraph"
    );
    
    private StreamingChatLanguageModel streamingChatLanguageModel;
    private ChatLanguageModel chatLanguageModel;
    private StateSerializer<MainGraphState> stateSerializer;
    private DataAnalysisToolCollection toolCollection;
    private ParallelExecutionConfig parallelExecutionConfig;
    @SuppressWarnings("unused")
    private String username;
    @SuppressWarnings("unused")
    private String requestId;

    /**
     * 设置聊天语言模型
     */
    public DataAnalysisGraphBuilder chatLanguageModel(ChatLanguageModel chatLanguageModel) {
        this.chatLanguageModel = chatLanguageModel;
        return this;
    }

    /**
     * 设置流式聊天语言模型
     */
    public DataAnalysisGraphBuilder chatLanguageModel(StreamingChatLanguageModel streamingChatLanguageModel) {
        this.streamingChatLanguageModel = streamingChatLanguageModel;
        return this;
    }

    /**
     * 设置状态序列化器
     */
    public DataAnalysisGraphBuilder stateSerializer(StateSerializer<MainGraphState> stateSerializer) {
        this.stateSerializer = stateSerializer;
        return this;
    }

    /**
     * 设置工具集合
     */
    public DataAnalysisGraphBuilder toolCollection(DataAnalysisToolCollection toolCollection) {
        this.toolCollection = toolCollection;
        return this;
    }

    /**
     * 设置并行执行配置
     */
    public DataAnalysisGraphBuilder parallelExecutionConfig(ParallelExecutionConfig parallelExecutionConfig) {
        this.parallelExecutionConfig = parallelExecutionConfig;
        return this;
    }

    /**
     * 设置用户名
     */
    public DataAnalysisGraphBuilder username(String username) {
        this.username = username;
        return this;
    }

    /**
     * 设置请求ID
     */
    public DataAnalysisGraphBuilder requestId(String requestId) {
        this.requestId = requestId;
        return this;
    }

    /**
     * 构建状态图
     */
    public StateGraph<MainGraphState> build() throws GraphStateException {
        if (streamingChatLanguageModel != null && chatLanguageModel != null) {
            throw new IllegalArgumentException("chatLanguageModel and streamingChatLanguageModel are mutually exclusive!");
        }
        if (streamingChatLanguageModel == null && chatLanguageModel == null) {
            throw new IllegalArgumentException("a chatLanguageModel or streamingChatLanguageModel is required!");
        }

        // 创建智能体实例
        Map<String, BaseAgent> agents = createAgents();

        if (stateSerializer == null) {
            stateSerializer = AgentSerializers.MAIN_GRAPH.mainGraph();
        }
        
        // 序列化器已经设置完成

        // 创建队列用于流式输出
        BlockingQueue<AsyncGenerator.Data<StreamingOutput<MainGraphState>>> queue = new LinkedBlockingQueue<>();

        // 创建Planner节点 - 使用专门的CallAgent
        final var callPlanner = new CallAgent("planner", agents.get("planner"));
        callPlanner.setQueue(queue);
        
        // 创建TodoList解析器节点
        final var todoListParser = new TodoListParser();

        // 创建Scheduler节点 - 使用专门的CallAgent
        final var callScheduler = new CallAgent("scheduler", agents.get("scheduler"));
        callScheduler.setQueue(queue);


        // 创建Summary节点 - 使用专门的CallAgent
        final var callSummary = new CallAgent("summary", agents.get("summary"));
        callSummary.setQueue(queue);

        // 构建各个子图
        Map<String, CompiledGraph<MainGraphState>> subgraphs = buildSubgraphs();

        
        // 定义边条件 - 基于TodoList的路由决策
        final EdgeAction<MainGraphState> todoListRouting = (state) -> {
            Optional<TodoList> todoListOpt = state.getTodoList();
            if (todoListOpt.isEmpty()) {
                log.warn("没有TodoList，路由到summary");
                return "summary";
            }
            
            TodoList todoList = todoListOpt.get();
            TodoTask nextTask = todoList.getNextExecutableTask();
            
            if (nextTask == null) {
                log.info("没有可执行的任务，路由到summary");
                return "summary";
            }
            
            // TodoListParser只负责路由到Scheduler，具体的子图路由由Scheduler决定
            log.info("TodoListParser: 路由到Scheduler处理任务 {}", nextTask.getTaskId());
            return "scheduler";
        };

        // 定义边条件 - Scheduler路由决策
        final EdgeAction<MainGraphState> schedulerRouting = (state) -> {
            Optional<TodoList> todoListOpt = state.getTodoList();
            if (todoListOpt.isEmpty()) {
                log.warn("Scheduler: 没有TodoList，路由到summary");
                return "summary";
            }
            
            TodoList todoList = todoListOpt.get();
            TodoTask nextTask = todoList.getNextExecutableTask();
            
            if (nextTask == null) {
                log.info("Scheduler: 没有可执行的任务，路由到summary");
                return "summary";
            }
            
            // 根据assignedAgent路由到对应子图
            String agentName = nextTask.getAssignedAgent();
            log.info("Scheduler: 路由任务 {} 到子图: {}", nextTask.getTaskId(), agentName);
            
            String subgraphName = AGENT_TO_SUBGRAPH_MAP.get(agentName);
            if (subgraphName != null) {
                return subgraphName;
            } else {
                log.warn("Scheduler: 未知的智能体: {}，路由到summary", agentName);
                return "summary";
            }
        };

        // 定义边条件 - 子图执行完成后返回Planner
        final EdgeAction<MainGraphState> subgraphShouldContinue = (state) -> {
            return "planner";
        };

        // 构建状态图
        return new StateGraph<>(MessagesState.SCHEMA, stateSerializer)
                // Planner相关节点
                .addNode("planner", node_async(callPlanner))
                .addNode("todoListParser", node_async(todoListParser))
                .addNode("scheduler", node_async(callScheduler))
                
                // Summary节点
                .addNode("summary", node_async(callSummary))
                
                // 子图节点
                .addNode("statistical_analysis_subgraph", createSubgraphNode(subgraphs.get("statistical_analysis_subgraph"), "statisticalAnalysisAgent"))
                .addNode("data_visualization_subgraph", createSubgraphNode(subgraphs.get("data_visualization_subgraph"), "dataVisualizationAgent"))
                .addNode("web_search_subgraph", createSubgraphNode(subgraphs.get("web_search_subgraph"), "webSearchAgent"))
                .addNode("comprehensive_analysis_subgraph", createSubgraphNode(subgraphs.get("comprehensive_analysis_subgraph"), "comprehensiveAnalysisAgent"))
                
                // 边连接
                .addEdge(START, "planner")
                .addEdge("planner", "todoListParser")
                .addConditionalEdges("todoListParser",
                        edge_async(todoListRouting),
                        Map.of(
                                "scheduler", "scheduler",
                                "summary", "summary"))
                .addConditionalEdges("scheduler",
                        edge_async(schedulerRouting),
                        Map.of(
                                "statistical_analysis_subgraph", "statistical_analysis_subgraph",
                                "data_visualization_subgraph", "data_visualization_subgraph",
                                "web_search_subgraph", "web_search_subgraph",
                                "comprehensive_analysis_subgraph", "comprehensive_analysis_subgraph",
                                "summary", "summary"))
                // 子图返回Planner的路由
                .addConditionalEdges("statistical_analysis_subgraph", edge_async(subgraphShouldContinue), Map.of("planner", "planner"))
                .addConditionalEdges("data_visualization_subgraph", edge_async(subgraphShouldContinue), Map.of("planner", "planner"))
                .addConditionalEdges("web_search_subgraph", edge_async(subgraphShouldContinue), Map.of("planner", "planner"))
                .addConditionalEdges("comprehensive_analysis_subgraph", edge_async(subgraphShouldContinue), Map.of("planner", "planner"))
                .addEdge("summary", END);
    }

    /**
     * 从状态中提取任务信息
     */
    private TodoTask extractTaskFromState(MainGraphState state) {
        try {
            // 1. 首先尝试从TodoList中获取下一个可执行任务
            Optional<TodoList> todoListOpt = state.getTodoList();
            if (todoListOpt.isPresent()) {
                TodoList todoList = todoListOpt.get();
                
                // 优先获取进行中的任务
                List<TodoTask> inProgressTasks = todoList.getInProgressTasks();
                if (!inProgressTasks.isEmpty()) {
                    // 如果有进行中的任务，返回第一个（通常只有一个）
                    TodoTask currentTask = inProgressTasks.get(0);
                    log.info("提取到进行中的任务: {} - {}", currentTask.getTaskId(), currentTask.getDescription());
                    return currentTask;
                }
                
                // 如果没有进行中的任务，获取下一个可执行任务
                TodoTask nextTask = todoList.getNextExecutableTask();
                if (nextTask != null) {
                    log.info("提取到下一个可执行任务: {} - {}", nextTask.getTaskId(), nextTask.getDescription());
                    return nextTask;
                }
                
                log.warn("TodoList中没有可执行的任务");
            } else {
                log.warn("状态中没有TodoList");
            }
            
            // 2. 如果TodoList中没有任务，尝试从消息历史中推断任务
            // 检查最后一条消息是否包含任务信息
            Optional<ChatMessage> lastMessageOpt = state.lastMessage();
            if (lastMessageOpt.isPresent()) {
                ChatMessage lastMessage = lastMessageOpt.get();
                    String messageContent = lastMessage.text();
                
                // 尝试从消息内容中提取任务信息
                if (messageContent.contains("统计分析") || messageContent.contains("statistical_analysis")) {
                    return new TodoTask("inferred_statistical", "执行统计分析任务", "statisticalAnalysisAgent");
                } else if (messageContent.contains("数据可视化") || messageContent.contains("data_visualization")) {
                    return new TodoTask("inferred_visualization", "执行数据可视化任务", "dataVisualizationAgent");
                } else if (messageContent.contains("网络搜索") || messageContent.contains("web_search")) {
                    return new TodoTask("inferred_web_search", "执行网络搜索任务", "webSearchAgent");
                } else if (messageContent.contains("综合分析") || messageContent.contains("comprehensive_analysis")) {
                    return new TodoTask("inferred_comprehensive", "执行综合分析任务", "comprehensiveAnalysisAgent");
                }
            }
            
            // 3. 如果无法推断，返回默认任务
            log.warn("无法从状态中提取任务信息，返回默认任务");
            return new TodoTask("default_task", "执行数据分析任务", "statisticalAnalysisAgent");
            
        } catch (Exception e) {
            log.error("提取任务信息时发生错误: {}", e.getMessage(), e);
            return new TodoTask("error_task", "执行默认数据分析任务", "statisticalAnalysisAgent");
        }
    }

    /**
     * 创建任务消息
     */
    private List<ChatMessage> createTaskMessage(TodoTask task, MainGraphState state) {
        List<ChatMessage> messages = new ArrayList<>();
        
        String instructionContent = getSchedulerInstruction(state)
            .orElse(String.format("请执行以下任务：%s", task.getDescription()));
        
        messages.add(UserMessage.from(instructionContent));
        
        return messages;
    }
    
    /**
     * 获取Scheduler的指令内容
     */
    private Optional<String> getSchedulerInstruction(MainGraphState state) {
        return state.lastMessage()
            .filter(msg -> msg instanceof AiMessage)
            .map(msg -> ((AiMessage) msg).text())
            .filter(instruction -> instruction != null && !instruction.trim().isEmpty());
    }
    

    /**
     * 从子图结果中提取摘要并更新状态
     */
    private Map<String, Object> extractSummaryFromSubgraphResult(
            Map<String, Object> subgraphResult, 
            MainGraphState state, 
            String agentName) {
        
        // 提取子图的最终响应
        String subgraphResponse = extractFinalResponseFromSubgraph(subgraphResult);
        
        // 使用Builder模式更新状态
        MainGraphState updatedState = state.withSubgraphResult(agentName, subgraphResponse);
        
        // 返回更新后的状态
        Map<String, Object> result = new HashMap<>();
        result.put("subgraphResults", updatedState.getSubgraphResults().orElse(new HashMap<>()));
        result.put("messages", updatedState.messages());
        
        return result;
    }

    /**
     * 从子图结果中提取最终响应
     */
    private String extractFinalResponseFromSubgraph(Map<String, Object> subgraphResult) {
        try {
            Object finalResponseObj = subgraphResult.get("finalResponse");
            if (finalResponseObj != null) {
                return finalResponseObj.toString();
            }
        } catch (Exception e) {
            log.warn("Failed to extract final response from subgraph result: {}", e.getMessage());
        }
        return "子图执行完成";
    }

    
    /**
     * 构建所有子图
     */
    private Map<String, CompiledGraph<MainGraphState>> buildSubgraphs() throws GraphStateException {
        Map<String, CompiledGraph<MainGraphState>> subgraphs = new HashMap<>();
        
        // 构建统计分析子图
        @SuppressWarnings("unchecked")
        CompiledGraph<MainGraphState> statisticalGraph = (CompiledGraph<MainGraphState>) (CompiledGraph<?>) new StatisticalAnalysisSubgraphBuilder()
                .chatLanguageModel(chatLanguageModel)
                .mainToolCollection(toolCollection)
                .parallelExecutionConfig(parallelExecutionConfig)
                .build()
                .compile();
        subgraphs.put("statistical_analysis_subgraph", statisticalGraph);
                
        // 构建数据可视化子图
        @SuppressWarnings("unchecked")
        CompiledGraph<MainGraphState> visualizationGraph = (CompiledGraph<MainGraphState>) (CompiledGraph<?>) new DataVisualizationSubgraphBuilder()
                .chatLanguageModel(chatLanguageModel)
                .mainToolCollection(toolCollection)
                .parallelExecutionConfig(parallelExecutionConfig)
                .build()
                .compile();
        subgraphs.put("data_visualization_subgraph", visualizationGraph);
                
        // 构建网络搜索子图
        @SuppressWarnings("unchecked")
        CompiledGraph<MainGraphState> webSearchGraph = (CompiledGraph<MainGraphState>) (CompiledGraph<?>) new WebSearchSubgraphBuilder()
                .chatLanguageModel(chatLanguageModel)
                .mainToolCollection(toolCollection)
                .parallelExecutionConfig(parallelExecutionConfig)
                .build()
                .compile();
        subgraphs.put("web_search_subgraph", webSearchGraph);
                
        // 构建综合分析子图
        @SuppressWarnings("unchecked")
        CompiledGraph<MainGraphState> comprehensiveGraph = (CompiledGraph<MainGraphState>) (CompiledGraph<?>) new ComprehensiveAnalysisSubgraphBuilder()
                .chatLanguageModel(chatLanguageModel)
                .mainToolCollection(toolCollection)
                .parallelExecutionConfig(parallelExecutionConfig)
                .build()
                .compile();
        subgraphs.put("comprehensive_analysis_subgraph", comprehensiveGraph);
                
        return subgraphs;
    }
    
    /**
     * 创建所有智能体实例
     */
    private Map<String, BaseAgent> createAgents() {
        Map<String, BaseAgent> agents = new HashMap<>();
        
        // 创建Planner智能体
        agents.put("planner", BaseAgent.builder()
                .chatLanguageModel(chatLanguageModel)
                .streamingChatLanguageModel(streamingChatLanguageModel)
                .tools(toolCollection.getToolsByAgentName("planner"))
                .agentName("planner")
                .build());
                
        // 创建Scheduler智能体
        agents.put("scheduler", BaseAgent.builder()
                .chatLanguageModel(chatLanguageModel)
                .streamingChatLanguageModel(streamingChatLanguageModel)
                .agentName("scheduler")
                .build());
                
        // 创建Summary智能体
        agents.put("summary", BaseAgent.builder()
                .chatLanguageModel(chatLanguageModel)
                .streamingChatLanguageModel(streamingChatLanguageModel)
                .agentName("summary")
                .build());
                
        return agents;
    }
    
    /**
     * 创建子图节点
     */
    private AsyncNodeAction<MainGraphState> createSubgraphNode(
            CompiledGraph<MainGraphState> subgraph, String agentName) {
        return node_async((MainGraphState state) -> {
            TodoTask task = extractTaskFromState(state);
            var input = Map.<String, Object>of("messages", createTaskMessage(task, state));
            return subgraph.stream(input)
                    .forEachAsync(System.out::println)
                    .thenApply((res) -> {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> result = (Map<String, Object>) res;
                        return extractSummaryFromSubgraphResult(result, state, agentName);
                    })
                    .join(); // 等待CompletableFuture完成
        });
    }
}
