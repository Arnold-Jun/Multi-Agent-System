package com.zhouruojun.travelingagent.agent.builder;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

import com.zhouruojun.travelingagent.agent.BaseAgent;
import com.zhouruojun.travelingagent.agent.actions.CallPreprocessorAgent;
import com.zhouruojun.travelingagent.agent.actions.CallPlannerAgent;
import com.zhouruojun.travelingagent.agent.actions.ExecuteTools;
import com.zhouruojun.travelingagent.agent.actions.CallSchedulerAgent;
import com.zhouruojun.travelingagent.agent.actions.CallSummaryAgent;
import com.zhouruojun.travelingagent.agent.actions.TodoListParser;
import com.zhouruojun.travelingagent.agent.actions.UserInput;
import com.zhouruojun.travelingagent.agent.parser.SchedulerResponseParser;
import com.zhouruojun.travelingagent.agent.state.SubgraphState;
import com.zhouruojun.travelingagent.agent.state.subgraph.SubgraphStateManager;
import com.zhouruojun.travelingagent.agent.state.subgraph.ToolExecutionHistory;
import com.zhouruojun.travelingagent.config.ParallelExecutionConfig;
import com.zhouruojun.travelingagent.config.CheckpointConfig;
import com.zhouruojun.travelingagent.agent.builder.subgraph.MetaSearchSubgraphBuilder;
import com.zhouruojun.travelingagent.agent.builder.subgraph.ItineraryPlannerSubgraphBuilder;
import com.zhouruojun.travelingagent.agent.builder.subgraph.BookingSubgraphBuilder;
import com.zhouruojun.travelingagent.agent.builder.subgraph.OnTripSubgraphBuilder;
import com.zhouruojun.travelingagent.agent.serializers.AgentSerializers;
import com.zhouruojun.travelingagent.agent.state.MainGraphState;
import com.zhouruojun.travelingagent.agent.state.main.TodoTask;
import com.zhouruojun.travelingagent.mcp.TravelingToolProviderManager;
import com.zhouruojun.travelingagent.prompts.PromptManager;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver;
import org.bsc.async.AsyncGenerator;
import org.bsc.langgraph4j.streaming.StreamingOutput;

import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.EdgeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.bsc.langgraph4j.serializer.StateSerializer;


/**
 * 旅游智能体图构建器
 * 用于构建旅游智能体的执行图，支持子图执行
 */
@Slf4j
public class TravelingGraphBuilder {
    
    
    private StreamingChatLanguageModel streamingChatLanguageModel;
    private ChatLanguageModel chatLanguageModel;
    private StateSerializer<MainGraphState> stateSerializer;
    private TravelingToolProviderManager toolProviderManager;
    private ParallelExecutionConfig parallelExecutionConfig;
    
    // Checkpoint相关配置
    private BaseCheckpointSaver checkpointSaver;
    private CheckpointConfig checkpointConfig;
    
    // Scheduler相关依赖
    private SchedulerResponseParser schedulerResponseParser;
    
    // 子图状态管理器
    private SubgraphStateManager subgraphStateManager;
    
    // 提示词管理器
    private PromptManager promptManager;

    /**
     * 设置聊天语言模型
     */
    public TravelingGraphBuilder chatLanguageModel(ChatLanguageModel chatLanguageModel) {
        this.chatLanguageModel = chatLanguageModel;
        return this;
    }

    /**
     * 设置流式聊天语言模型
     */
    public TravelingGraphBuilder chatLanguageModel(StreamingChatLanguageModel streamingChatLanguageModel) {
        this.streamingChatLanguageModel = streamingChatLanguageModel;
        return this;
    }

    /**
     * 设置状态序列化器
     */
    public TravelingGraphBuilder stateSerializer(StateSerializer<MainGraphState> stateSerializer) {
        this.stateSerializer = stateSerializer;
        return this;
    }

    
    public TravelingGraphBuilder toolProviderManager(TravelingToolProviderManager toolProviderManager) {
        this.toolProviderManager = toolProviderManager;
        return this;
    }

    /**
     * 设置并行执行配置
     */
    public TravelingGraphBuilder parallelExecutionConfig(ParallelExecutionConfig parallelExecutionConfig) {
        this.parallelExecutionConfig = parallelExecutionConfig;
        return this;
    }


    /**
     * 设置检查点保存器
     */
    public TravelingGraphBuilder checkpointSaver(BaseCheckpointSaver checkpointSaver) {
        this.checkpointSaver = checkpointSaver;
        return this;
    }

    /**
     * 设置检查点配置
     */
    public TravelingGraphBuilder checkpointConfig(CheckpointConfig checkpointConfig) {
        this.checkpointConfig = checkpointConfig;
        return this;
    }

    /**
     * 设置调度器响应解析器
     */
    public TravelingGraphBuilder schedulerResponseParser(SchedulerResponseParser schedulerResponseParser) {
        this.schedulerResponseParser = schedulerResponseParser;
        return this;
    }

    /**
     * 设置子图状态管理器
     */
    public TravelingGraphBuilder subgraphStateManager(SubgraphStateManager subgraphStateManager) {
        this.subgraphStateManager = subgraphStateManager;
        return this;
    }

    /**
     * 设置提示词管理器
     */
    public TravelingGraphBuilder promptManager(PromptManager promptManager) {
        this.promptManager = promptManager;
        return this;
    }

    /**
     * 构建主图
     */
    public StateGraph<MainGraphState> build() throws GraphStateException {
        if (streamingChatLanguageModel != null && chatLanguageModel != null) {
            throw new IllegalArgumentException("chatLanguageModel and streamingChatLanguageModel are mutually exclusive!");
        }
        if (streamingChatLanguageModel == null && chatLanguageModel == null) {
            throw new IllegalArgumentException("a chatLanguageModel or streamingChatLanguageModel is required!");
        }
        
        log.info("Building main traveling graph");
        
        validateParameters();
        
        // 创建智能体实例
        Map<String, BaseAgent> agents = createAgents();
        
        // 创建队列用于流式输出
        BlockingQueue<AsyncGenerator.Data<StreamingOutput<MainGraphState>>> queue = new LinkedBlockingQueue<>();
        
        // 创建智能体
        CallPreprocessorAgent callPreprocessor = new CallPreprocessorAgent("preprocessor", agents.get("preprocessor"), promptManager);
        callPreprocessor.setQueue(queue);
        
        CallPlannerAgent callPlanner = new CallPlannerAgent("planner", agents.get("planner"), promptManager);
        callPlanner.setQueue(queue);
        
        TodoListParser todoListParser = createTodoListParser();
        
        CallSchedulerAgent callScheduler = new CallSchedulerAgent("scheduler", agents.get("scheduler"), schedulerResponseParser, promptManager);
        callScheduler.setQueue(queue);
        
        CallSummaryAgent callSummary = new CallSummaryAgent("summary", agents.get("summary"), promptManager);
        callSummary.setQueue(queue);
        
        UserInput userInput = new UserInput();
        
        // 创建ExecuteTools节点（用于preprocessor的工具调用）
        ExecuteTools<MainGraphState> executeTools = new ExecuteTools<>("preprocessor", agents.get("preprocessor"), toolProviderManager, parallelExecutionConfig);
        
        // 创建子图
        Map<String, CompiledGraph<SubgraphState>> subgraphs = createSubgraphs();

        if (stateSerializer == null) {
            stateSerializer = AgentSerializers.MAIN_GRAPH.mainGraph();
        }
        
        // 构建主图
        return new StateGraph<MainGraphState>(MessagesState.SCHEMA, stateSerializer)
                .addNode("preprocessor", node_async(callPreprocessor))
                .addNode("executeTools", node_async(executeTools))
                .addNode("planner", node_async(callPlanner))
                .addNode("todoListParser", node_async(todoListParser))
                .addNode("scheduler", node_async(callScheduler))
                .addNode("summary", node_async(callSummary))
                .addNode("userInput", node_async(userInput))
                .addNode("metaSearchAgent", createSubgraphNode(subgraphs.get("metaSearchAgent"), "metaSearchAgent"))
                .addNode("itineraryPlannerAgent", createSubgraphNode(subgraphs.get("itineraryPlannerAgent"), "itineraryPlannerAgent"))
                .addNode("bookingAgent", createSubgraphNode(subgraphs.get("bookingAgent"), "bookingAgent"))
                .addNode("onTripAgent", createSubgraphNode(subgraphs.get("onTripAgent"), "onTripAgent"))
                
                // 边连接
                .addEdge(START, "preprocessor")
                .addConditionalEdges("preprocessor",
                        edge_async(preprocessorRouting),
                        Map.of(
                                "planner", "planner",
                                "Finish", END,
                                "action", "executeTools",
                                "retry", "preprocessor"))
                .addConditionalEdges("executeTools",
                        edge_async(actionShouldContinue),
                        Map.of("callback", "preprocessor"))
                .addEdge("planner", "todoListParser")
                .addConditionalEdges("todoListParser",
                        edge_async(todoListRouting),
                        Map.of(
                                "scheduler", "scheduler",
                                "summary", "summary",
                                "planner", "planner"))
                .addConditionalEdges("scheduler",
                        edge_async(schedulerRouting),
                        Map.of(
                                "metaSearchAgent", "metaSearchAgent",
                                "itineraryPlannerAgent", "itineraryPlannerAgent",
                                "bookingAgent", "bookingAgent",
                                "onTripAgent", "onTripAgent",
                                "userInput", "userInput",
                                "planner", "planner",
                                "summary", "summary",
                                "Finish", END))
                .addConditionalEdges("metaSearchAgent", edge_async(subgraphShouldContinue), Map.of("scheduler", "scheduler"))
                .addConditionalEdges("itineraryPlannerAgent", edge_async(subgraphShouldContinue), Map.of("scheduler", "scheduler"))
                .addConditionalEdges("bookingAgent", edge_async(subgraphShouldContinue), Map.of("scheduler", "scheduler"))
                .addConditionalEdges("onTripAgent", edge_async(subgraphShouldContinue), Map.of("scheduler", "scheduler"))
                .addEdge("userInput", "scheduler")
                .addEdge("summary", END);
    }

    /**
     * 创建所有智能体实例
     */
    private Map<String, BaseAgent> createAgents() {
        Map<String, BaseAgent> agents = new HashMap<>();
        
        // 创建Preprocessor智能体
        agents.put("preprocessor", BaseAgent.builder()
                .chatLanguageModel(chatLanguageModel)
                .streamingChatLanguageModel(streamingChatLanguageModel)
                .tools(toolProviderManager.getToolSpecificationsByAgent("preprocessor"))
                .agentName("preprocessor")
                .prompt(promptManager.getMainGraphSystemPrompt("preprocessor", "DEFAULT"))
                .build());
        
        // 创建Planner智能体
        agents.put("planner", BaseAgent.builder()
                .chatLanguageModel(chatLanguageModel)
                .streamingChatLanguageModel(streamingChatLanguageModel)
                .tools(toolProviderManager.getToolSpecificationsByAgent("planner"))
                .agentName("planner")
                .prompt(promptManager.getMainGraphSystemPrompt("planner", "INITIAL"))
                .build());
                
        // 创建Scheduler智能体
        agents.put("scheduler", BaseAgent.builder()
                .chatLanguageModel(chatLanguageModel)
                .streamingChatLanguageModel(streamingChatLanguageModel)
                .agentName("scheduler")
                .prompt(promptManager.getMainGraphSystemPrompt("scheduler", "INITIAL_SCHEDULING"))
                .build());
                
        // 创建Summary智能体
        agents.put("summary", BaseAgent.builder()
                .chatLanguageModel(chatLanguageModel)
                .streamingChatLanguageModel(streamingChatLanguageModel)
                .agentName("summary")
                .prompt(promptManager.getMainGraphSystemPrompt("summary", "DEFAULT"))
                .build());
                
        return agents;
    }

    /**
     * 创建TodoList解析器
     */
    private TodoListParser createTodoListParser() {
        return new TodoListParser();
    }


    /**
     * 创建子图
     */
    private Map<String, CompiledGraph<SubgraphState>> createSubgraphs() 
            throws GraphStateException {
        
        Map<String, CompiledGraph<SubgraphState>> subgraphs = new HashMap<>();
        
        // 构建子图的通用配置
        boolean checkpointEnabled = checkpointConfig != null ? checkpointConfig.isEnabled() : true;
        
        // 构建MetaSearchAgent子图
        subgraphs.put("metaSearchAgent", 
            buildSubgraph(new MetaSearchSubgraphBuilder(), "metaSearchAgent", checkpointEnabled));
                
        // 构建ItineraryPlannerAgent子图
        subgraphs.put("itineraryPlannerAgent", 
            buildSubgraph(new ItineraryPlannerSubgraphBuilder(), "itineraryPlannerAgent", checkpointEnabled));
                
        // 构建BookingAgent子图
        subgraphs.put("bookingAgent", 
            buildSubgraph(new BookingSubgraphBuilder(), "bookingAgent", checkpointEnabled));
                
        // 构建OnTripAgent子图
        subgraphs.put("onTripAgent", 
            buildSubgraph(new OnTripSubgraphBuilder(), "onTripAgent", checkpointEnabled));
                
        return subgraphs;
    }

    /**
     * 构建单个子图的辅助方法
     */
    private CompiledGraph<SubgraphState> buildSubgraph(
            BaseSubgraphBuilder<SubgraphState> builder,
            String agentName, 
            boolean checkpointEnabled) throws GraphStateException {
        
        String namespace = checkpointConfig != null ? 
            checkpointConfig.getSubgraphNamespace(agentName) : agentName;
            
        return (CompiledGraph<SubgraphState>) builder
                .chatLanguageModel(chatLanguageModel)
                .toolProviderManager(toolProviderManager)
                .parallelExecutionConfig(parallelExecutionConfig)
                .checkpointSaver(checkpointSaver)
                .enableCheckpoint(checkpointEnabled)
                .checkpointNamespace(namespace)
                .promptManager(promptManager)
                .build()
                .compile();
    }

    /**
     * 创建子图节点
     */
    private AsyncNodeAction<MainGraphState> createSubgraphNode(
            CompiledGraph<SubgraphState> subgraph, String agentName) {
        return node_async((MainGraphState state) -> {
            // 获取Scheduler提供的所有信息
            String schedulerTaskDescription = state.getValue("subgraphTaskDescription")
                    .map(Object::toString)
                    .orElse(null);
            String schedulerContext = state.getValue("subgraphContext")
                    .map(Object::toString)
                    .orElse(null);
            String schedulerTaskId = state.getValue("subgraphTaskId")
                    .map(Object::toString)
                    .orElse("unknown_task");
            
            String sessionId = state.getValue("sessionId")
                    .map(Object::toString)
                    .orElse("default");
            
            // 创建用于子图执行的任务对象
            // 优先使用Scheduler提供的描述，否则使用默认描述
            String effectiveTaskDescription = (schedulerTaskDescription != null && !schedulerTaskDescription.isEmpty()) 
                    ? schedulerTaskDescription 
                    : "执行旅游相关任务";
            
            TodoTask effectiveTask = new TodoTask(
                    schedulerTaskId,
                    effectiveTaskDescription,
                    agentName
            );
            
            log.info("构建子图任务: {} - {}", schedulerTaskId, effectiveTaskDescription);
            
            // 尝试加载历史状态
            Map<String, Object> subgraphStateData = new HashMap<>();
            boolean stateRestored = false;
            
            if (subgraphStateManager != null) {
                Optional<Map<String, Object>> restoredState = subgraphStateManager
                        .loadSubgraphState(sessionId, agentName, agentName);
                
                if (restoredState.isPresent()) {
                    // 恢复历史状态
                    subgraphStateData.putAll(restoredState.get());
                    stateRestored = true;
                    log.info("✓ 恢复子图历史状态: sessionId={}, agentName={}, executionCount={}", 
                            sessionId, agentName, subgraphStateData.get("executionCount"));
                }
            }
            
            // 如果没有恢复状态，则创建新状态
            if (!stateRestored) {
                log.info("创建全新子图状态: sessionId={}, agentName={}", sessionId, agentName);
                
                // 初始化工具执行历史管理对象
                subgraphStateData.put("toolExecutionHistory", 
                    new ToolExecutionHistory(20));
            }
            
            // 更新当前任务信息（每次都是新的）
            subgraphStateData.put("messages", createTaskMessage(effectiveTask, state));
            subgraphStateData.put("currentTask", effectiveTask);
            
            // 添加Scheduler提供的上下文信息
            if (schedulerContext != null && !schedulerContext.isEmpty()) {
                subgraphStateData.put("subgraphContext", schedulerContext);
                log.info("子图获取到Scheduler提供的context: {}", 
                        schedulerContext.substring(0, Math.min(100, schedulerContext.length())));
            }
            if (schedulerTaskDescription != null && !schedulerTaskDescription.isEmpty()) {
                subgraphStateData.put("subgraphTaskDescription", schedulerTaskDescription);
                log.info("子图获取到Scheduler提供的taskDescription: {}", schedulerTaskDescription);
            }
            
            // 添加子图类型信息（直接使用智能体名称）
            subgraphStateData.put("subgraphType", agentName);
            
            // 添加其他必要的状态信息
            if (state.getOriginalUserQuery().isPresent()) {
                subgraphStateData.put("originalUserQuery", state.getOriginalUserQuery().get());
            }
            if (state.getSubgraphResults().isPresent()) {
                subgraphStateData.put("subgraphResults", state.getSubgraphResults().get());
            }
            
            SubgraphState subgraphState = new SubgraphState(subgraphStateData);

            var input = subgraphState.data();
            return subgraph.stream(input)
                    .forEachAsync(output -> {})
                    .thenApply((res) -> {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> result = (Map<String, Object>) res;
                        
                        // 提取最终的子图状态并保存
                        if (subgraphStateManager != null && result instanceof Map) {
                            try {
                                SubgraphState finalSubgraphState = new SubgraphState(result);
                                subgraphStateManager.saveSubgraphState(sessionId, agentName, agentName, finalSubgraphState);
                                log.info("✓ 保存子图状态: sessionId={}, agentName={}", sessionId, agentName);
                            } catch (Exception e) {
                                log.warn("保存子图状态失败: {}", e.getMessage());
                            }
                        }
                        
                        return extractSummaryFromSubgraphResult(result, state, agentName);
                    })
                    .join();
        });
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
        result.put("todoList", updatedState.getTodoList().orElse(null));
        result.put("replanCount", updatedState.getReplanCount());
        
        // 问号启发式：检测子图响应是否以问号结尾
        // 如果以问号结尾，设置finalResponse让Scheduler可以检测到
        if (endsWithQuestionMark(subgraphResponse)) {
            log.info("检测到子图响应以问号结尾，设置finalResponse用于问号启发式路由");
            result.put("finalResponse", subgraphResponse);
        }
        
        return result;
    }
    
    /**
     * 检查字符串是否以问号结尾（支持中英文、全角半角）
     */
    private boolean endsWithQuestionMark(String text) {
        if (text == null || text.trim().isEmpty()) {
            return false;
        }
        
        String trimmed = text.trim();
        char lastChar = trimmed.charAt(trimmed.length() - 1);
        
        // 支持英文问号、中文问号、全角问号
        return lastChar == '?' || lastChar == '？';
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
     * Preprocessor路由逻辑
     */
    private final EdgeAction<MainGraphState> preprocessorRouting = (state) -> {
        // 定义Preprocessor路由的有效目标节点
        List<String> preprocessorChildren = new ArrayList<>();
        preprocessorChildren.add("planner");
        preprocessorChildren.add("Finish");
        preprocessorChildren.add("action");
        preprocessorChildren.add("retry");
        
        return state.next()
                .filter(preprocessorChildren::contains)
                .orElseThrow(() -> new IllegalStateException("CallPreprocessorAgent必须设置有效的next字段"));
    };

    /**
     * TodoList路由逻辑
     */
    private final EdgeAction<MainGraphState> todoListRouting = (state) -> {
        // 定义TodoList路由的有效目标节点
        List<String> todoListChildren = new ArrayList<>();
        todoListChildren.add("scheduler");
        todoListChildren.add("summary");
        todoListChildren.add("planner");
        
        return state.next()
                .filter(todoListChildren::contains)
                .orElseThrow(() -> new IllegalStateException("TodoListParser必须设置有效的next字段"));
    };

    /**
     * Scheduler路由逻辑
     */
    private final EdgeAction<MainGraphState> schedulerRouting = (state) -> {
        // 定义Scheduler路由的有效目标节点
        List<String> schedulerChildren = new ArrayList<>();
        schedulerChildren.add("metaSearchAgent");
        schedulerChildren.add("itineraryPlannerAgent");
        schedulerChildren.add("bookingAgent");
        schedulerChildren.add("onTripAgent");
        schedulerChildren.add("planner");
        schedulerChildren.add("summary");
        schedulerChildren.add("userInput");
        schedulerChildren.add("Finish");
        
        return state.next()
                .filter(schedulerChildren::contains)
                .orElseThrow(() -> new IllegalStateException("CallSchedulerAgent必须设置有效的next字段"));
    };

    /**
     * 子图路由逻辑
     */
    private final EdgeAction<MainGraphState> subgraphShouldContinue = (state) -> "scheduler";

    /**
     * 工具执行回调边条件
     */
    private final EdgeAction<MainGraphState> actionShouldContinue = (state) -> "callback";

    /**
     * 验证参数
     */
    private void validateParameters() {
        if (chatLanguageModel == null && streamingChatLanguageModel == null) {
            throw new IllegalArgumentException("ChatLanguageModel or StreamingChatLanguageModel is required");
        }
        if (toolProviderManager == null) {
            throw new IllegalArgumentException("ToolProviderManager is required");
        }
        if (schedulerResponseParser == null) {
            throw new IllegalArgumentException("SchedulerResponseParser is required");
        }
    }
}