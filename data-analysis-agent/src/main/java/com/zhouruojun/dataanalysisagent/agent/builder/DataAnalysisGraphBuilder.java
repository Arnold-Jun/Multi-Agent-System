package com.zhouruojun.dataanalysisagent.agent.builder;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

import com.zhouruojun.dataanalysisagent.agent.BaseAgent;
import com.zhouruojun.dataanalysisagent.agent.actions.CallAgent;
import com.zhouruojun.dataanalysisagent.agent.actions.TodoListParser;
import com.zhouruojun.dataanalysisagent.agent.todo.TaskStatus;
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
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;

import java.util.*;

import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import lombok.extern.slf4j.Slf4j;
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

        // 创建Planner节点 - 使用特殊的CallAgent来处理动态prompt
        final var callPlanner = new CallAgent<MainGraphState>("planner", agents.get("planner")) {
            @Override
            public Map<String, Object> apply(MainGraphState state) {
                // 构建动态prompt
                String dynamicPrompt = buildPlannerPrompt(state);
                
                // 创建包含动态prompt的消息
                List<ChatMessage> messages = List.of(UserMessage.from(dynamicPrompt));
                
                // 创建新的状态，包含构建的消息
                Map<String, Object> newState = new HashMap<>(state.data());
                newState.put("messages", messages);
                
                // 调用父类的apply方法
                return super.apply(new MainGraphState(newState));
            }
        };
        callPlanner.setQueue(queue);
        
        // 创建TodoList解析器节点
        final var todoListParser = new TodoListParser();

        // 创建Scheduler节点 - 使用特殊的CallAgent来处理动态prompt
        final var callScheduler = new CallAgent<MainGraphState>("scheduler", agents.get("scheduler")) {
            @Override
            public Map<String, Object> apply(MainGraphState state) {
                // 构建动态prompt
                String dynamicPrompt = buildSchedulerPrompt(state);
                
                // 创建包含动态prompt的消息
                List<ChatMessage> messages = List.of(UserMessage.from(dynamicPrompt));
                
                // 创建新的状态，包含构建的消息
                Map<String, Object> newState = new HashMap<>(state.data());
                newState.put("messages", messages);
                
                // 调用父类的apply方法
                Map<String, Object> result = super.apply(new MainGraphState(newState));
                
                // 将Scheduler的输出保存到schedulerOutput状态中
                if (result.containsKey("finalResponse")) {
                    String schedulerOutput = (String) result.get("finalResponse");
                    state.setSchedulerOutput(schedulerOutput);
                    
                    // 创建新的可变Map来返回结果
                    Map<String, Object> newResult = new HashMap<>(result);
                    newResult.put("schedulerOutput", schedulerOutput);

                    return newResult;
                }
                
                return result;
            }
        };
        callScheduler.setQueue(queue);


        // 创建Summary节点 - 使用特殊的CallAgent来处理状态信息
        final var callSummary = new CallAgent<MainGraphState>("summary", agents.get("summary")) {
            @Override
            public Map<String, Object> apply(MainGraphState state) {
                // 构建Summary上下文消息
                String summaryContext = buildSummaryContext(state);
                List<ChatMessage> messages = List.of(UserMessage.from(summaryContext));
                
                // 创建新的状态，包含构建的消息
                Map<String, Object> newState = new HashMap<>(state.data());
                newState.put("messages", messages);
                
                // 调用父类的apply方法
                return super.apply(new MainGraphState(newState));
            }
        };
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
     * 创建任务消息 - 优先使用Scheduler输出
     */
    private List<ChatMessage> createTaskMessage(TodoTask task, MainGraphState state) {
        List<ChatMessage> messages = new ArrayList<>();
        
        // 优先使用Scheduler的输出
        Optional<String> schedulerOutput = state.getSchedulerOutput();
        if (schedulerOutput.isPresent()) {
            messages.add(UserMessage.from(schedulerOutput.get()));
            log.info("使用Scheduler输出作为子图输入");
        } else {
            // 如果Scheduler输出为空，使用任务描述作为backup
            String taskMessage = String.format("请执行以下任务：%s", task.getDescription());
            messages.add(UserMessage.from(taskMessage));
            log.info("使用任务描述作为子图输入（Scheduler输出为空）");
        }
        
        return messages;
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
     * 构建Planner的动态prompt
     */
    private String buildPlannerPrompt(MainGraphState state) {
        // 从messages中提取用户查询
        String userQuery = extractUserQueryFromMessages(state);
        
        // 获取子图结果
        Map<String, String> subgraphResults = state.getSubgraphResults().orElse(null);
        
        // 获取TodoList信息
        String todoListInfo = state.getTodoList()
                .map(TodoList::getSummary)
                .orElse("无任务列表");
        
        // 使用PromptTemplateManager构建prompt
        return PromptTemplateManager.instance.buildPlannerPrompt(userQuery, subgraphResults, todoListInfo);
    }
    
    /**
     * 构建Scheduler的动态prompt
     */
    private String buildSchedulerPrompt(MainGraphState state) {
        // 获取输入信息
        TodoList todoList = state.getTodoList().orElse(null);
        Map<String, String> subgraphResults = state.getSubgraphResults().orElse(new HashMap<>());

        if (todoList == null) {
            return "无任务列表，无法生成上下文";
        }

        // 获取下一步要执行的任务
        TodoTask nextTask = getNextExecutableTask(todoList);
        if (nextTask == null) {
            return "没有可执行的任务，所有任务已完成";
        }

        // 构建prompt
        StringBuilder prompt = new StringBuilder();

        // 1. 添加任务列表状态
        prompt.append("**当前任务列表状态**：\n");
        prompt.append(todoList.getSummary()).append("\n\n");

        // 2. 添加已完成任务的结果
        prompt.append("**已完成任务的结果**：\n");
        todoList.getTasks().stream()
            .filter(task -> task.getStatus() == TaskStatus.COMPLETED)
            .forEach(task -> {
                prompt.append("- ").append(task.getDescription())
                      .append("：").append(task.getResult()).append("\n");
            });
        prompt.append("\n");

        // 3. 添加子图执行结果
        prompt.append("**子图执行结果**：\n");
        subgraphResults.forEach((agent, result) -> {
            prompt.append("- ").append(agent).append("：").append(result).append("\n");
        });
        prompt.append("\n");

        // 4. 添加下一步任务
        prompt.append("**下一步要执行的任务**：\n");
        prompt.append(nextTask.getDescription()).append("\n\n");

        // 5. 添加推理指导
        prompt.append("**你的职责**：\n");
        prompt.append("1. 分析已完成任务的结果\n");
        prompt.append("2. 整合相关的子图执行结果\n");
        prompt.append("3. 为下一步任务准备完整的上下文\n");
        prompt.append("4. 确保子图能够获得执行所需的所有信息\n\n");

        prompt.append("请输出一个完整的上下文，包含任务描述、前序任务结果、相关分析数据和执行指导。");

        return prompt.toString();
    }
    
    /**
     * 获取下一个可执行的任务
     */
    private TodoTask getNextExecutableTask(TodoList todoList) {
        return todoList.getTasks().stream()
            .filter(task -> task.getStatus() == TaskStatus.PENDING)
            .min(Comparator.comparing(TodoTask::getOrder))
            .orElse(null);
    }
    
    /**
     * 从messages中提取用户查询
     */
    private String extractUserQueryFromMessages(MainGraphState state) {
        // 首先尝试从originalUserQuery获取
        Optional<String> originalQuery = state.getOriginalUserQuery();
        if (originalQuery.isPresent()) {
            return originalQuery.get();
        }
        
        // 如果originalUserQuery为空，从messages中提取
        List<ChatMessage> messages = state.messages();
        for (ChatMessage message : messages) {
            if (message instanceof UserMessage) {
                UserMessage userMessage = (UserMessage) message;
                String content = userMessage.text();
                if (content != null && !content.trim().isEmpty()) {
                    return content.trim();
                }
            }
        }
        
        // 如果都没有找到，返回默认值
        return "用户查询";
    }
    
    private String buildSummaryContext(MainGraphState state) {
        StringBuilder context = new StringBuilder();
        
        // 添加原始用户查询
        state.getOriginalUserQuery().ifPresent(query -> {
            context.append("原始用户查询: ").append(query).append("\n\n");
        });
        
        // 添加任务列表统计
        context.append("任务执行统计:\n");
        context.append(state.getTodoListStatistics()).append("\n\n");
        
        // 添加各子智能体的执行结果
        context.append("子智能体执行结果:\n");
        state.getSubgraphResults().ifPresent(results -> {
            if (results.isEmpty()) {
                context.append("无子智能体执行结果\n");
            } else {
                for (Map.Entry<String, String> entry : results.entrySet()) {
                    String agentName = entry.getKey();
                    String result = entry.getValue();
                    context.append("【").append(agentName).append("】\n");
                    context.append(result).append("\n\n");
                }
            }
        });
        
        // 添加任务列表详情
        state.getTodoList().ifPresent(todoList -> {
            context.append("任务列表详情:\n");
            context.append(todoList.getSummary()).append("\n");
        });
        
        context.append("\n请基于以上信息生成完整的数据分析报告。");
        
        return context.toString();
    }
    
    /**
     * 构建所有子图
     */
    private Map<String, CompiledGraph<MainGraphState>> buildSubgraphs() throws GraphStateException {
        Map<String, CompiledGraph<MainGraphState>> subgraphs = new HashMap<>();
        
        // 构建统计分析子图
        subgraphs.put("statistical_analysis_subgraph", 
            new StatisticalAnalysisSubgraphBuilder()
                .chatLanguageModel(chatLanguageModel)
                .mainToolCollection(toolCollection)
                .parallelExecutionConfig(parallelExecutionConfig)
                .build()
                .compile());
                
        // 构建数据可视化子图
        subgraphs.put("data_visualization_subgraph",
            new DataVisualizationSubgraphBuilder()
                .chatLanguageModel(chatLanguageModel)
                .mainToolCollection(toolCollection)
                .parallelExecutionConfig(parallelExecutionConfig)
                .build()
                .compile());
                
        // 构建网络搜索子图
        subgraphs.put("web_search_subgraph",
            new WebSearchSubgraphBuilder()
                .chatLanguageModel(chatLanguageModel)
                .mainToolCollection(toolCollection)
                .parallelExecutionConfig(parallelExecutionConfig)
                .build()
                .compile());
                
        // 构建综合分析子图
        subgraphs.put("comprehensive_analysis_subgraph",
            new ComprehensiveAnalysisSubgraphBuilder()
                .chatLanguageModel(chatLanguageModel)
                .mainToolCollection(toolCollection)
                .parallelExecutionConfig(parallelExecutionConfig)
                .build()
                .compile());
                
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
                .prompt(PromptTemplateManager.instance.getPrompt("planner"))
                .build());
                
        // 创建Scheduler智能体
        agents.put("scheduler", BaseAgent.builder()
                .chatLanguageModel(chatLanguageModel)
                .streamingChatLanguageModel(streamingChatLanguageModel)
                .agentName("scheduler")
                .prompt("你是一个智能调度器，负责为子图执行准备完整的上下文信息。") // 基础prompt，会被动态替换
                .build());
                
        // 创建Summary智能体
        agents.put("summary", BaseAgent.builder()
                .chatLanguageModel(chatLanguageModel)
                .streamingChatLanguageModel(streamingChatLanguageModel)
                .agentName("summary")
                .prompt(PromptTemplateManager.instance.getPrompt("summary"))
                .build());
                
        return agents;
    }
    
    /**
     * 创建子图节点
     */
    private org.bsc.langgraph4j.action.AsyncNodeAction<MainGraphState> createSubgraphNode(
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
