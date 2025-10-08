package com.zhouruojun.jobsearchagent.agent.builder;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

import com.zhouruojun.jobsearchagent.agent.BaseAgent;
import com.zhouruojun.jobsearchagent.agent.actions.CallPlannerAgent;
import com.zhouruojun.jobsearchagent.agent.actions.CallSchedulerAgent;
import com.zhouruojun.jobsearchagent.agent.actions.CallSummaryAgent;
import com.zhouruojun.jobsearchagent.agent.actions.TodoListParser;
import com.zhouruojun.jobsearchagent.agent.actions.UserInput;
import com.zhouruojun.jobsearchagent.agent.parser.SchedulerResponseParser;
import com.zhouruojun.jobsearchagent.agent.state.SubgraphState;
import com.zhouruojun.jobsearchagent.config.ParallelExecutionConfig;
import com.zhouruojun.jobsearchagent.config.CheckpointConfig;
import com.zhouruojun.jobsearchagent.config.FailureThresholdConfig;
import com.zhouruojun.jobsearchagent.agent.builder.subgraph.JobInfoCollectionSubgraphBuilder;
import com.zhouruojun.jobsearchagent.agent.builder.subgraph.ResumeAnalysisOptimizationSubgraphBuilder;
import com.zhouruojun.jobsearchagent.agent.builder.subgraph.JobSearchExecutionSubgraphBuilder;
import com.zhouruojun.jobsearchagent.agent.serializers.AgentSerializers;
import com.zhouruojun.jobsearchagent.agent.state.MainGraphState;
import com.zhouruojun.jobsearchagent.agent.todo.TodoTask;
import com.zhouruojun.jobsearchagent.common.PromptTemplateManager;
import com.zhouruojun.jobsearchagent.tools.JobSearchToolCollection;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver;

import java.util.*;

import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.async.AsyncGenerator;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.EdgeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.bsc.langgraph4j.serializer.StateSerializer;
import org.bsc.langgraph4j.streaming.StreamingOutput;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * 求职智能体图构建器
 * 用于构建求职智能体的执行图，支持子图执行
 */
@Slf4j
public class JobSearchGraphBuilder {
    
    
    private StreamingChatLanguageModel streamingChatLanguageModel;
    private ChatLanguageModel chatLanguageModel;
    private StateSerializer<MainGraphState> stateSerializer;
    private JobSearchToolCollection toolCollection;
    private ParallelExecutionConfig parallelExecutionConfig;
    @SuppressWarnings("unused")
    private String username;
    @SuppressWarnings("unused")
    private String requestId;
    
    // Checkpoint相关配置
    private BaseCheckpointSaver checkpointSaver;
    private CheckpointConfig checkpointConfig;
    
    // Scheduler相关依赖
    private SchedulerResponseParser schedulerResponseParser;
    private FailureThresholdConfig failureThresholdConfig;

    /**
     * 设置聊天语言模型
     */
    public JobSearchGraphBuilder chatLanguageModel(ChatLanguageModel chatLanguageModel) {
        this.chatLanguageModel = chatLanguageModel;
        return this;
    }

    /**
     * 设置流式聊天语言模型
     */
    public JobSearchGraphBuilder chatLanguageModel(StreamingChatLanguageModel streamingChatLanguageModel) {
        this.streamingChatLanguageModel = streamingChatLanguageModel;
        return this;
    }

    /**
     * 设置状态序列化器
     */
    public JobSearchGraphBuilder stateSerializer(StateSerializer<MainGraphState> stateSerializer) {
        this.stateSerializer = stateSerializer;
        return this;
    }

    /**
     * 设置工具集合
     */
    public JobSearchGraphBuilder toolCollection(JobSearchToolCollection toolCollection) {
        this.toolCollection = toolCollection;
        return this;
    }

    /**
     * 设置并行执行配置
     */
    public JobSearchGraphBuilder parallelExecutionConfig(ParallelExecutionConfig parallelExecutionConfig) {
        this.parallelExecutionConfig = parallelExecutionConfig;
        return this;
    }
    
    /**
     * 设置checkpoint保存器
     */
    public JobSearchGraphBuilder checkpointSaver(BaseCheckpointSaver checkpointSaver) {
        this.checkpointSaver = checkpointSaver;
        return this;
    }
    
    /**
     * 设置checkpoint配置
     */
    public JobSearchGraphBuilder checkpointConfig(CheckpointConfig checkpointConfig) {
        this.checkpointConfig = checkpointConfig;
        return this;
    }

    /**
     * 设置用户名
     */
    public JobSearchGraphBuilder username(String username) {
        this.username = username;
        return this;
    }

    /**
     * 设置请求ID
     */
    public JobSearchGraphBuilder requestId(String requestId) {
        this.requestId = requestId;
        return this;
    }

    /**
     * 设置Scheduler响应解析器
     */
    public JobSearchGraphBuilder schedulerResponseParser(SchedulerResponseParser schedulerResponseParser) {
        this.schedulerResponseParser = schedulerResponseParser;
        return this;
    }

    /**
     * 设置失败阈值配置
     */
    public JobSearchGraphBuilder failureThresholdConfig(FailureThresholdConfig failureThresholdConfig) {
        this.failureThresholdConfig = failureThresholdConfig;
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
        
        // 创建队列用于流式输出
        BlockingQueue<AsyncGenerator.Data<StreamingOutput<MainGraphState>>> queue = new LinkedBlockingQueue<>();

        // 创建Planner节点 - 使用专门的CallPlannerAgent
        final var callPlanner = new CallPlannerAgent("planner", agents.get("planner"));
        callPlanner.setQueue(queue);
        
        // 创建TodoList解析器节点
        final var todoListParser = new TodoListParser();

        // 创建Scheduler节点
        final var callScheduler = new CallSchedulerAgent("scheduler", agents.get("scheduler"), 
                schedulerResponseParser, failureThresholdConfig);
        callScheduler.setQueue(queue);

        // 创建Summary节点 - 使用CallSummaryAgent
        final var callSummary = new CallSummaryAgent("summary", agents.get("summary"));
        callSummary.setQueue(queue);

        // 构建各个子图
        Map<String, CompiledGraph<SubgraphState>> subgraphs = buildSubgraphs();


        // 定义TodoList路由的有效目标节点
        List<String> todoListChildren = new ArrayList<>();
        todoListChildren.add("scheduler");
        todoListChildren.add("summary");
        todoListChildren.add("planner");
        
        final EdgeAction<MainGraphState> todoListRouting = (state) ->
                state.next()
                    .filter(todoListChildren::contains)
                    .orElseThrow(() -> new IllegalStateException("TodoListParser必须设置有效的next字段"));

        // 定义Scheduler路由的有效目标节点
        List<String> schedulerChildren = new ArrayList<>();
        schedulerChildren.add("job_info_collection_subgraph");
        schedulerChildren.add("resume_analysis_optimization_subgraph");
        schedulerChildren.add("job_search_execution_subgraph");
        schedulerChildren.add("planner");
        schedulerChildren.add("summary");
        schedulerChildren.add("userInput");  // 添加userInput节点
        
        final EdgeAction<MainGraphState> schedulerRouting = (state) ->
                state.next()
                    .filter(schedulerChildren::contains)
                    .orElseThrow(() -> new IllegalStateException("CallSchedulerAgent必须设置有效的next字段"));

        // 定义边条件 - 子图执行完成后返回Scheduler
        final EdgeAction<MainGraphState> subgraphShouldContinue = (state) -> {
            return "scheduler";
        };

        // 构建状态图
        return new StateGraph<>(MessagesState.SCHEMA, stateSerializer)
                // Planner相关节点
                .addNode("planner", node_async(callPlanner))
                .addNode("todoListParser", node_async(todoListParser))
                .addNode("scheduler", node_async(callScheduler))
                
                // Summary节点
                .addNode("summary", node_async(callSummary))
                
                // UserInput节点 - 用于A2A交互中的用户输入
                .addNode("userInput", node_async(new UserInput()))
                
                // 子图节点
                .addNode("job_info_collection_subgraph", createSubgraphNode(subgraphs.get("job_info_collection_subgraph"), "jobInfoCollectorAgent"))
                .addNode("resume_analysis_optimization_subgraph", createSubgraphNode(subgraphs.get("resume_analysis_optimization_subgraph"), "resumeAnalysisOptimizationAgent"))
                .addNode("job_search_execution_subgraph", createSubgraphNode(subgraphs.get("job_search_execution_subgraph"), "jobSearchExecutionAgent"))
                
                // 边连接
                .addEdge(START, "planner")
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
                                "job_info_collection_subgraph", "job_info_collection_subgraph",
                                "resume_analysis_optimization_subgraph", "resume_analysis_optimization_subgraph",
                                "job_search_execution_subgraph", "job_search_execution_subgraph",
                                "planner", "planner",
                                "summary", "summary",
                                "userInput", "userInput"))  // 添加userInput路由
                // UserInput返回Scheduler
                .addEdge("userInput", "scheduler")
                // 子图返回Scheduler的路由
                .addConditionalEdges("job_info_collection_subgraph", edge_async(subgraphShouldContinue), Map.of("scheduler", "scheduler"))
                .addConditionalEdges("resume_analysis_optimization_subgraph", edge_async(subgraphShouldContinue), Map.of("scheduler", "scheduler"))
                .addConditionalEdges("job_search_execution_subgraph", edge_async(subgraphShouldContinue), Map.of("scheduler", "scheduler"))
                .addEdge("summary", END);
    }

    /**
     * 根据智能体名称确定assignedAgent
     */
    private String determineAssignedAgent(String agentName) {
        return switch (agentName) {
            case "job_info_collection_subgraph" -> "jobInfoCollectorAgent";
            case "resume_analysis_optimization_subgraph" -> "resumeAnalysisOptimizationAgent";
            case "job_search_execution_subgraph" -> "jobSearchExecutionAgent";
            default -> "unknownAgent";
        };
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
    private Map<String, CompiledGraph<SubgraphState>> buildSubgraphs() throws GraphStateException {
        Map<String, CompiledGraph<SubgraphState>> subgraphs = new HashMap<>();
        
        // 构建子图的通用配置
        boolean checkpointEnabled = checkpointConfig != null ? checkpointConfig.isEnabled() : true;
        
        // 构建岗位信息收集子图
        subgraphs.put("job_info_collection_subgraph", 
            buildSubgraph(new JobInfoCollectionSubgraphBuilder(), "job_info_collection", checkpointEnabled));
                
        // 构建简历分析优化子图
        subgraphs.put("resume_analysis_optimization_subgraph", 
            buildSubgraph(new ResumeAnalysisOptimizationSubgraphBuilder(), "resume_analysis_optimization", checkpointEnabled));
                
        // 构建求职执行子图
        subgraphs.put("job_search_execution_subgraph", 
            buildSubgraph(new JobSearchExecutionSubgraphBuilder(), "job_search_execution", checkpointEnabled));
                
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
                .mainToolCollection(toolCollection)
                .parallelExecutionConfig(parallelExecutionConfig)
                .checkpointSaver(checkpointSaver)
                .enableCheckpoint(checkpointEnabled)
                .checkpointNamespace(namespace)
                .build()
                .compile();
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
                .prompt(PromptTemplateManager.instance.getPlannerPrompt())
                .build());
                
        // 创建Scheduler智能体
        agents.put("scheduler", BaseAgent.builder()
                .chatLanguageModel(chatLanguageModel)
                .streamingChatLanguageModel(streamingChatLanguageModel)
                .agentName("scheduler")
                .prompt(PromptTemplateManager.instance.getSchedulerPrompt())
                .build());
                
        // 创建Summary智能体
        agents.put("summary", BaseAgent.builder()
                .chatLanguageModel(chatLanguageModel)
                .streamingChatLanguageModel(streamingChatLanguageModel)
                .agentName("summary")
                .prompt(PromptTemplateManager.instance.getSummaryPrompt())
                .build());
                
        return agents;
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
            
            String assignedAgent = determineAssignedAgent(agentName);
            
            // 创建用于子图执行的任务对象
            // 优先使用Scheduler提供的描述，否则使用默认描述
            String effectiveTaskDescription = (schedulerTaskDescription != null && !schedulerTaskDescription.isEmpty()) 
                    ? schedulerTaskDescription 
                    : "执行求职相关任务";
            
            TodoTask effectiveTask = new TodoTask(
                    schedulerTaskId,
                    effectiveTaskDescription,
                    assignedAgent
            );
            
            log.info("构建子图任务: {} - {}", schedulerTaskId, effectiveTaskDescription);
            
            // 创建子图状态，包含必要的上下文信息
            Map<String, Object> subgraphStateData = new HashMap<>();
            subgraphStateData.put("messages", createTaskMessage(effectiveTask, state));
            
            // 保存任务到状态中
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
            
            // 添加子图类型信息
            String subgraphType = determineSubgraphType(agentName);
            subgraphStateData.put("subgraphType", subgraphType);
            
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
                        return extractSummaryFromSubgraphResult(result, state, agentName);
                    })
                    .join();
        });
    }

    /**
     * 根据智能体名称确定子图类型
     */
    private String determineSubgraphType(String agentName) {
        switch (agentName) {
            case "jobInfoCollectorAgent":
                return "job_info_collection";
            case "resumeAnalysisOptimizationAgent":
                return "resume_analysis_optimization";
            case "jobSearchExecutionAgent":
                return "job_search_execution";
            default:
                log.warn("未知的智能体名称: {}, 使用默认子图类型", agentName);
                return "job_info_collection";
        }
    }
}
