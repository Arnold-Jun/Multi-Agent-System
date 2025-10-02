package com.zhouruojun.dataanalysisagent.agent.actions;

import com.zhouruojun.dataanalysisagent.agent.BaseAgent;
import com.zhouruojun.dataanalysisagent.agent.state.BaseAgentState;
import com.zhouruojun.dataanalysisagent.agent.state.MainGraphState;
import com.zhouruojun.dataanalysisagent.agent.todo.TodoTask;
import com.zhouruojun.dataanalysisagent.common.MessageFilter;
import com.zhouruojun.dataanalysisagent.common.PromptTemplateManager;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.bsc.async.AsyncGenerator;
import org.bsc.langgraph4j.action.NodeAction;
import org.bsc.langgraph4j.streaming.StreamingOutput;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;

/**
 * 主图智能体调用节点
 * 负责调用Planner、Scheduler、Summary等主图智能体
 * 管理提示词构建和状态更新
 */
@Slf4j
public class CallAgent<T extends BaseAgentState>  implements NodeAction<MainGraphState> {

    private static final int MAX_RETRIES = 3;
    private final BaseAgent agent;
    private final String agentName;

    /**
     * 流式输出队列
     */
    @SuppressWarnings("unused")
    private BlockingQueue<AsyncGenerator.Data<StreamingOutput<T>>> queue;

    public CallAgent(@NonNull String agentName, @NonNull BaseAgent agent) {
        this.agentName = agentName;
        this.agent = agent;
    }


    /**
     * 设置流式输出队列
     *
     * @param queue 队列
     */
    public void setQueue(BlockingQueue<AsyncGenerator.Data<StreamingOutput<T>>> queue) {
        this.queue = queue;
    }

    @Override
    public Map<String, Object> apply(MainGraphState state) {
        log.info("Calling main graph agent: {}", agentName);

        try {
            return applyWithRetry(state);
        } catch (Exception e) {
            log.error("Failed to call main graph agent: {}", agentName, e);
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("error", e.getMessage());
            return errorResult;
        }
    }

    /**
     * 带重试机制的智能体调用
     */
    private Map<String, Object> applyWithRetry(MainGraphState state) {
        int retryCount = 0;
        Exception lastException = null;

        while (retryCount < MAX_RETRIES) {
            try {
                // 构建消息
                List<ChatMessage> messages = buildMessages(state);
                
                log.info("Calling {} agent (attempt {}/{})",
                        agentName, retryCount + 1, MAX_RETRIES);

                // 调用智能体
                var response = agent.execute(messages);

                AiMessage aiMessage = response.aiMessage();

                // 创建过滤后的AI消息，使用MessageFilter
                AiMessage filteredAiMessage = MessageFilter.createSubGraphFilteredMessage(aiMessage);

                return Map.of("messages", List.of(filteredAiMessage));

            } catch (Exception e) {
                lastException = e;
                retryCount++;
                log.warn("{} agent call failed (attempt {}/{}): {}",
                        agentName, retryCount, MAX_RETRIES, e.getMessage());
                
                if (retryCount < MAX_RETRIES) {
                    try {
                        Thread.sleep(1000 * retryCount); // 递增延迟
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        throw new RuntimeException("Failed to call main graph agent: " + agentName + " after " + MAX_RETRIES + " retries", lastException);
    }

    /**
     * 构建发送给智能体的消息
     */
    private List<ChatMessage> buildMessages(MainGraphState state) {
        switch (agentName) {
            case "planner":
                return buildPlannerMessages(state);
            case "scheduler":
                return buildSchedulerMessages(state);
            case "summary":
                return buildSummaryMessages(state);
            default:
                throw new IllegalArgumentException("Unknown agent: " + agentName);
        }
    }


    // ==================== Prompt构建方法 ====================

    /**
     * 构建Planner的消息
     * 使用PromptTemplateManager的动态System/User拆分方法
     */
        private List<ChatMessage> buildPlannerMessages(MainGraphState state) {
        List<ChatMessage> messages = new ArrayList<>();
        
        // 获取动态数据
        String originalUserQuery = state.getOriginalUserQuery().orElse("用户查询");
        Map<String, String> subgraphResults = state.getSubgraphResults().orElse(new HashMap<>());
        String todoListInfo = state.getTodoListStatistics();
        
        // 使用buildPlannerPrompt方法动态选择System/User提示词
        if (subgraphResults.isEmpty() && todoListInfo.equals("无任务列表")) {
            // 初始模式
            String systemPrompt = PromptTemplateManager.instance.buildPlannerInitialSystemPrompt();
            String userPrompt = PromptTemplateManager.instance.buildPlannerInitialUserPrompt(originalUserQuery);
            messages.add(SystemMessage.from(systemPrompt));
            messages.add(UserMessage.from(userPrompt));
        } else {
            // 更新模式
            String systemPrompt = PromptTemplateManager.instance.buildPlannerUpdateSystemPrompt();
            String userPrompt = PromptTemplateManager.instance.buildPlannerUpdateUserPrompt(originalUserQuery, todoListInfo, subgraphResults);
            messages.add(SystemMessage.from(systemPrompt));
            messages.add(UserMessage.from(userPrompt));
        }
        
        return messages;
    }

    /**
     * 构建Scheduler的消息
     * 使用PromptTemplateManager的动态System/User拆分方法
     */
    private List<ChatMessage> buildSchedulerMessages(MainGraphState state) {
        List<ChatMessage> messages = new ArrayList<>();
        
        // 获取动态数据
        String originalUserQuery = state.getOriginalUserQuery().orElse("用户查询");
        Map<String, String> subgraphResults = state.getSubgraphResults().orElse(new HashMap<>());
        
        // 构建当前任务信息
        StringBuilder currentTaskInfo = new StringBuilder();
        state.getTodoList().ifPresent(todoList -> {
            List<TodoTask> pendingTasks = todoList.getPendingTasks();
            if (!pendingTasks.isEmpty()) {
                TodoTask currentTask = pendingTasks.get(0);
                currentTaskInfo.append("任务ID: ").append(currentTask.getUniqueId()).append("\n");
                currentTaskInfo.append("任务描述: ").append(currentTask.getDescription()).append("\n");
                currentTaskInfo.append("分配智能体: ").append(currentTask.getAssignedAgent()).append("\n");
                currentTaskInfo.append("执行顺序: ").append(currentTask.getOrder()).append("\n");
            }
        });
        
        // 构建筑图结果信息
        StringBuilder subgraphResultsInfo = new StringBuilder();
        if (!subgraphResults.isEmpty()) {
            subgraphResults.forEach((agent, result) -> {
                subgraphResultsInfo.append(agent).append(": ").append(result).append("\n");
            });
        }
        
        // 直接使用PromptTemplateManager的方法进行System/User拆分
        if (subgraphResults.isEmpty()) {
            // 初始模式
            String systemPrompt = PromptTemplateManager.instance.buildSchedulerInitialSystemPrompt();
            String userPrompt = PromptTemplateManager.instance.buildSchedulerInitialUserPrompt(originalUserQuery, currentTaskInfo.toString());
            messages.add(SystemMessage.from(systemPrompt));
            messages.add(UserMessage.from(userPrompt));
        } else {
            // 更新模式
            String systemPrompt = PromptTemplateManager.instance.buildSchedulerUpdateSystemPrompt();
            String userPrompt = PromptTemplateManager.instance.buildSchedulerUpdateUserPrompt(originalUserQuery, currentTaskInfo.toString(), subgraphResultsInfo.toString());
            messages.add(SystemMessage.from(systemPrompt));
            messages.add(UserMessage.from(userPrompt));
        }
        
        return messages;
    }

    /**
     * 构建Summary的消息
     * 使用PromptTemplateManager的专用System/User拆分方法
     */
    private List<ChatMessage> buildSummaryMessages(MainGraphState state) {
        List<ChatMessage> messages = new ArrayList<>();
        
        // 获取动态数据
        String originalUserQuery = state.getOriginalUserQuery().orElse("用户查询");
        Map<String, String> subgraphResults = state.getSubgraphResults().orElse(new HashMap<>());
        
        // 构建筑图结果信息
        StringBuilder subgraphResultsInfo = new StringBuilder();
        if (!subgraphResults.isEmpty()) {
            subgraphResults.forEach((agent, result) -> {
                subgraphResultsInfo.append(agent).append(": ").append(result).append("\n");
            });
        } else {
            subgraphResultsInfo.append("暂无子图执行结果");
        }
        
        String systemPrompt = PromptTemplateManager.instance.buildSummarySystemPrompt();
        String userPrompt = PromptTemplateManager.instance.buildSummaryUserPrompt(originalUserQuery, subgraphResultsInfo.toString());
        messages.add(SystemMessage.from(systemPrompt));
        messages.add(UserMessage.from(userPrompt));
        
        return messages;
    }
}