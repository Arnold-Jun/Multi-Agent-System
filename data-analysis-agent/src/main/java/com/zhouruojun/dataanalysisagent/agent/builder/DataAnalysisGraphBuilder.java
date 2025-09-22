package com.zhouruojun.dataanalysisagent.agent.builder;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhouruojun.dataanalysisagent.agent.BaseAgent;
import com.zhouruojun.dataanalysisagent.agent.actions.CallAgent;
import com.zhouruojun.dataanalysisagent.agent.actions.ExecuteTools;
import com.zhouruojun.dataanalysisagent.agent.builder.subgraph.DataLoadingSubgraphBuilder;
import com.zhouruojun.dataanalysisagent.agent.builder.subgraph.StatisticalAnalysisSubgraphBuilder;
import com.zhouruojun.dataanalysisagent.agent.builder.subgraph.DataVisualizationSubgraphBuilder;
import com.zhouruojun.dataanalysisagent.agent.builder.subgraph.WebSearchSubgraphBuilder;
import com.zhouruojun.dataanalysisagent.agent.task.RouteResponse;
import com.zhouruojun.dataanalysisagent.agent.task.TaskDescription;
import com.zhouruojun.dataanalysisagent.agent.serializers.AgentSerializers;
import com.zhouruojun.dataanalysisagent.agent.state.AgentMessageState;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import com.zhouruojun.dataanalysisagent.common.PromptTemplateManager;
import com.zhouruojun.dataanalysisagent.tools.DataAnalysisToolCollection;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import java.util.ArrayList;
import java.util.List;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import lombok.extern.slf4j.Slf4j;
import org.bsc.async.AsyncGenerator;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.EdgeAction;
import org.bsc.langgraph4j.serializer.StateSerializer;
import org.bsc.langgraph4j.streaming.StreamingOutput;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * 数据分析智能体图构建器
 * 用于构建数据分析智能体的执行图，支持子图路由
 */
@Slf4j
public class DataAnalysisGraphBuilder {
    
    /**
     * JSON解析器
     */
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    private StreamingChatLanguageModel streamingChatLanguageModel;
    private ChatLanguageModel chatLanguageModel;
    private StateSerializer<AgentMessageState> stateSerializer;
    private DataAnalysisToolCollection toolCollection;
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
    public DataAnalysisGraphBuilder stateSerializer(StateSerializer<AgentMessageState> stateSerializer) {
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
    public StateGraph<AgentMessageState> build() throws GraphStateException {
        if (streamingChatLanguageModel != null && chatLanguageModel != null) {
            throw new IllegalArgumentException("chatLanguageModel and streamingChatLanguageModel are mutually exclusive!");
        }
        if (streamingChatLanguageModel == null && chatLanguageModel == null) {
            throw new IllegalArgumentException("a chatLanguageModel or streamingChatLanguageModel is required!");
        }

        // 创建主智能体实例 - 使用统一的工具获取方式
        var agent = BaseAgent.builder()
                .chatLanguageModel(chatLanguageModel)
                .streamingChatLanguageModel(streamingChatLanguageModel)
                .tools(toolCollection.getToolsByAgentName("dataAnalysisSupervisor"))
                .agentName("dataAnalysisSupervisor")
                .prompt(PromptTemplateManager.instance.getDataAnalysisSupervisorPrompt())
                .build();

        if (stateSerializer == null) {
            stateSerializer = AgentSerializers.STD.object();
        }

        // 创建队列用于流式输出
        BlockingQueue<AsyncGenerator.Data<StreamingOutput<AgentMessageState>>> queue = new LinkedBlockingQueue<>();

        // 创建主智能体节点和执行工具节点
        final var callAgent = new CallAgent("dataAnalysisSupervisor", agent);
        callAgent.setQueue(queue);
        
        // 创建主智能体的工具执行节点
        final var executeTools = new ExecuteTools("dataAnalysisSupervisorAction", agent, toolCollection);

        // 构建各个子图 - 只需要传递主工具集合，子图构建器会自动获取对应的工具
        CompiledGraph<AgentMessageState> dataLoadingSubgraph = new DataLoadingSubgraphBuilder()
                .chatLanguageModel(chatLanguageModel)
                .mainToolCollection(toolCollection)
                .build()
                .compile();

        CompiledGraph<AgentMessageState> statisticalAnalysisSubgraph = new StatisticalAnalysisSubgraphBuilder()
                .chatLanguageModel(chatLanguageModel)
                .mainToolCollection(toolCollection)
                .build()
                .compile();

        CompiledGraph<AgentMessageState> dataVisualizationSubgraph = new DataVisualizationSubgraphBuilder()
                .chatLanguageModel(chatLanguageModel)
                .mainToolCollection(toolCollection)
                .build()
                .compile();

        CompiledGraph<AgentMessageState> webSearchSubgraph = new WebSearchSubgraphBuilder()
                .chatLanguageModel(chatLanguageModel)
                .mainToolCollection(toolCollection)
                .build()
                .compile();

        // 定义边条件 - 主智能体的工具执行路由
        final EdgeAction<AgentMessageState> supervisorShouldContinue = (state) -> {
            var lastMessage = state.lastMessage();
            if (lastMessage.isPresent() && lastMessage.get() instanceof AiMessage) {
                AiMessage aiMessage = (AiMessage) lastMessage.get();
                if (aiMessage.hasToolExecutionRequests()) {
                    return "action";
                }
            }
            return "finish";
        };

        // 定义边条件 - 主智能体动作节点路由逻辑
        final EdgeAction<AgentMessageState> supervisorActionShouldRoute = (state) -> {
            return extractRouteFromToolExecution(state);
        };

        final EdgeAction<AgentMessageState> subgraphShouldContinue = (state) -> {
            // 子图执行完成后，返回到主智能体继续对话
            return "callback";
        };

        // 构建状态图
        return new StateGraph<>(AgentMessageState.SCHEMA, stateSerializer)
                .addNode("dataAnalysisSupervisor", node_async(callAgent))
                .addNode("dataAnalysisSupervisorAction", node_async(executeTools))
                .addNode("data_loading_subgraph", state -> {
                    TaskDescription task = extractTaskFromState(state);
                    var input = Map.<String, Object>of("messages", createTaskMessage(task));
                    return dataLoadingSubgraph.stream(input)
                            .forEachAsync(System.out::println)
                            .thenApply((res) -> {
                                // 提取子图的最终响应并添加到主图状态中
                                return extractSummaryFromSubgraphResult(res, state);
                            });
                })
                .addNode("statistical_analysis_subgraph", state -> {
                    TaskDescription task = extractTaskFromState(state);
                    var input = Map.<String, Object>of("messages", createTaskMessage(task));
                    return statisticalAnalysisSubgraph.stream(input)
                            .forEachAsync(System.out::println)
                            .thenApply((res) -> {
                                // 提取子图的最终响应并添加到主图状态中
                                return extractSummaryFromSubgraphResult(res, state);
                            });
                })
                .addNode("data_visualization_subgraph", state -> {
                    TaskDescription task = extractTaskFromState(state);
                    var input = Map.<String, Object>of("messages", createTaskMessage(task));
                    return dataVisualizationSubgraph.stream(input)
                            .forEachAsync(System.out::println)
                            .thenApply((res) -> {
                                // 提取子图的最终响应并添加到主图状态中
                                return extractSummaryFromSubgraphResult(res, state);
                            });
                })
                .addNode("web_search_subgraph", state -> {
                    TaskDescription task = extractTaskFromState(state);
                    var input = Map.<String, Object>of("messages", createTaskMessage(task));
                    return webSearchSubgraph.stream(input)
                            .forEachAsync(System.out::println)
                            .thenApply((res) -> {
                                // 提取子图的最终响应并添加到主图状态中
                                return extractSummaryFromSubgraphResult(res, state);
                            });
                })
                .addEdge(START, "dataAnalysisSupervisor")
                .addConditionalEdges("dataAnalysisSupervisor",
                        edge_async(supervisorShouldContinue),
                        Map.of(
                                "action", "dataAnalysisSupervisorAction",
                                "finish", END))
                .addConditionalEdges("dataAnalysisSupervisorAction",
                        edge_async(supervisorActionShouldRoute),
                        Map.of(
                                "data_loading", "data_loading_subgraph",
                                "statistical_analysis", "statistical_analysis_subgraph",
                                "data_visualization", "data_visualization_subgraph",
                                "web_search", "web_search_subgraph",
                                "callback", "dataAnalysisSupervisor"))
                .addConditionalEdges("data_loading_subgraph",
                        edge_async(subgraphShouldContinue),
                        Map.of("callback", "dataAnalysisSupervisor"))
                .addConditionalEdges("statistical_analysis_subgraph",
                        edge_async(subgraphShouldContinue),
                        Map.of("callback", "dataAnalysisSupervisor"))
                .addConditionalEdges("data_visualization_subgraph",
                        edge_async(subgraphShouldContinue),
                        Map.of("callback", "dataAnalysisSupervisor"))
                .addConditionalEdges("web_search_subgraph",
                        edge_async(subgraphShouldContinue),
                        Map.of("callback", "dataAnalysisSupervisor"));
    }

    /**
     * 从工具执行结果中提取路由信息
     */
    private String extractRouteFromToolExecution(AgentMessageState state) {
        var lastMessage = state.lastMessage();
        if (lastMessage.isEmpty()) {
            return "callback";
        }

        // 检查最后一条消息是否是工具执行结果
        if (lastMessage.get() instanceof ToolExecutionResultMessage) {
            ToolExecutionResultMessage toolResult =
                (ToolExecutionResultMessage) lastMessage.get();
            
            String resultText = toolResult.text();
            log.info("Tool execution result: {}", resultText);
            
            try {
                // 解析JSON格式的路由响应
                RouteResponse routeResponse = objectMapper.readValue(resultText, RouteResponse.class);
                
                if (routeResponse.isFinish()) {
                    return "callback"; // 返回主智能体处理完成
                } else if (routeResponse.isRoute()) {
                    return routeResponse.getAction();
                }
            } catch (Exception e) {
                log.warn("Failed to parse tool execution result as JSON: {}", e.getMessage());
            }
        }

        return "callback";
    }

    /**
     * 从状态中提取任务描述
     */
    private TaskDescription extractTaskFromState(AgentMessageState state) {
        var lastMessage = state.lastMessage();
        if (lastMessage.isEmpty() || !(lastMessage.get() instanceof ToolExecutionResultMessage)) {
            return TaskDescription.of("未知任务", "请执行相关任务");
        }

        ToolExecutionResultMessage toolMessage = (ToolExecutionResultMessage) lastMessage.get();
        String messageText = toolMessage.text().trim();

        try {
            RouteResponse routeResponse = objectMapper.readValue(messageText, RouteResponse.class);
            if (routeResponse.getTask() != null) {
                return routeResponse.getTask();
            }
        } catch (Exception e) {
            System.err.println("Failed to extract task from JSON: " + e.getMessage());
        }

        // 回退到使用原始消息
        return TaskDescription.of(messageText, "执行用户请求的任务");
    }

    /**
     * 创建任务消息
     */
    private UserMessage createTaskMessage(TaskDescription task) {
        StringBuilder messageBuilder = new StringBuilder();
        messageBuilder.append("任务查询: ").append(task.getQuery()).append("\n");
        messageBuilder.append("任务目的: ").append(task.getPurpose()).append("\n");
        
        if (task.getContext() != null && !task.getContext().trim().isEmpty()) {
            messageBuilder.append("上下文信息: ").append(task.getContext()).append("\n");
        }
        
        messageBuilder.append("\n请根据以上任务描述执行相应的操作。");
        
        return UserMessage.from(messageBuilder.toString());
    }

    /**
     * 从子图结果中提取最终响应并添加到主图状态中
     * 将finalResponse作为AiMessage添加到消息历史中，而不是保存到subgraphSummary
     */
    private Map<String, Object> extractSummaryFromSubgraphResult(Object subgraphResult, AgentMessageState originalState) {
        try {
            Map<String, Object> parentData = new HashMap<>(originalState.data());

            @SuppressWarnings("unchecked")
            Map<String, Object> resultMap = (Map<String, Object>) subgraphResult;
                
            // 获取子图的finalResponse
            Object finalResponseObj = resultMap.get("finalResponse");
            if (finalResponseObj != null) {
                String finalResponse = finalResponseObj.toString();
                
                // 将finalResponse作为AiMessage添加到消息历史中
                // 这样CallAgent就能通过state.messages()获取到子图的结果
                List<ChatMessage> currentMessages = new ArrayList<>(originalState.messages());
                AiMessage subgraphResultMessage = AiMessage.from(finalResponse);
                currentMessages.add(subgraphResultMessage);
                
                parentData.put("messages", currentMessages);
                
                // 同时保存到finalResponse字段，供控制器使用
                parentData.put("finalResponse", finalResponse);
            }

            return parentData;
        } catch (Exception e) {
            System.err.println("提取子图结果时出错: " + e.getMessage());
            return originalState.data();
        }
    }

}
