package com.zhouruojun.jobsearchagent.agent.actions;

import com.zhouruojun.jobsearchagent.agent.BaseAgent;
import com.zhouruojun.jobsearchagent.agent.dto.SchedulerResponse;
import com.zhouruojun.jobsearchagent.agent.parser.SchedulerResponseParser;
import com.zhouruojun.jobsearchagent.agent.state.MainGraphState;
import com.zhouruojun.jobsearchagent.agent.todo.TodoList;
import com.zhouruojun.jobsearchagent.agent.todo.TodoTask;
import com.zhouruojun.jobsearchagent.agent.todo.TaskStatus;
import com.zhouruojun.jobsearchagent.common.PromptTemplateManager;
import com.zhouruojun.jobsearchagent.config.FailureThresholdConfig;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 调度智能体调用节点
 * 专门用于主图的调度阶段，负责三个核心功能：
 * 1. 初始调度：从TodoListParser接收任务，提供上下文并路由到子图
 * 2. 结果处理：处理子图返回结果，判断任务完成状态，处理失败重试
 * 3. 失败阈值管理：当失败超过阈值时，路由回planner重新规划
 */
@Slf4j
public class CallSchedulerAgent extends CallAgent<MainGraphState> {

    private final SchedulerResponseParser schedulerResponseParser;
    private final FailureThresholdConfig failureThresholdConfig;

    /**
     * 构造函数
     *
     * @param agentName 智能体名称
     * @param agent 智能体实例
     * @param schedulerResponseParser 调度响应解析器
     * @param failureThresholdConfig 失败阈值配置
     */
    public CallSchedulerAgent(@NonNull String agentName, 
                             @NonNull BaseAgent agent,
                             @NonNull SchedulerResponseParser schedulerResponseParser,
                             @NonNull FailureThresholdConfig failureThresholdConfig) {
        super(agentName, agent);
        this.schedulerResponseParser = schedulerResponseParser;
        this.failureThresholdConfig = failureThresholdConfig;
    }

    @Override
    protected List<ChatMessage> buildMessages(MainGraphState state) {
        SchedulerScenario scenario = determineSchedulerScenario(state);
        
        List<ChatMessage> messages = buildMessagesForScenario(scenario, state);
        
        log.debug("调度智能体消息构建完成，场景: {}, 消息数量: {}", 
                scenario, messages.size());
        
        return messages;
    }
    
    /**
     * 根据场景构建消息
     */
    private List<ChatMessage> buildMessagesForScenario(SchedulerScenario scenario, MainGraphState state) {
        List<ChatMessage> messages = new ArrayList<>();
        
        switch (scenario) {
            case INITIAL_SCHEDULING -> {
                addSchedulerMessages(messages, 
                    PromptTemplateManager.instance.buildSchedulerInitialSystemPrompt(),
                    PromptTemplateManager.instance.buildSchedulerInitialUserPrompt(
                        state.getOriginalUserQuery().orElse("请帮我调度求职任务"),
                        state.getTodoListForScheduler()
                    )
                );
            }
            case RESULT_PROCESSING -> {
                addSchedulerMessages(messages,
                    PromptTemplateManager.instance.buildSchedulerUpdateSystemPrompt(),
                    buildResultProcessingUserPrompt(state)
                );
            }
        }
        
        return messages;
    }
    
    /**
     * 添加调度器消息的通用方法
     */
    private void addSchedulerMessages(List<ChatMessage> messages, String systemPrompt, String userPrompt) {
        messages.add(SystemMessage.from(systemPrompt));
        messages.add(UserMessage.from(userPrompt));
    }

    @Override
    protected Map<String, Object> processResponse(AiMessage originalMessage, AiMessage filteredMessage, MainGraphState state) {
        try {
            // 解析调度响应
            var parseResult = schedulerResponseParser.parseSchedulerResponse(filteredMessage.text());
            if (!parseResult.isSuccess()) {
                log.error("调度响应解析失败: {}", parseResult.getErrorMessage());
                return createErrorResult(state, "调度响应解析失败: " + parseResult.getErrorMessage());
            }
            
            SchedulerResponse response = parseResult.getResponse();
            
            // 处理任务状态更新
            Map<String, Object> result = processTaskUpdate(response, state);
            
            // 添加消息到结果中
            result.put("messages", List.of(filteredMessage));
            
            log.info("调度智能体 {} 完成调度，任务ID: {}, 状态: {}, 下一步: {}", 
                    agentName, 
                    response.getTaskUpdate().getTaskId(),
                    response.getTaskUpdate().getStatus(),
                    response.getNextAction().getNext());
            
            return result;
            
        } catch (Exception e) {
            log.error("调度智能体 {} 处理响应失败: {}", agentName, e.getMessage(), e);
            
            // 检查是否达到失败阈值
            if (isFailureThresholdExceeded(state)) {
                log.error("达到失败阈值，路由到planner重新规划");
                return createSimpleReplanRequest(state, "调度处理失败，达到失败阈值，需要重新规划");
            }
            
            // 重新尝试调度
            return Map.of(
                    "messages", List.of(filteredMessage),
                    "next", "scheduler"
            );
        }
    }

    /**
     * 判断调度场景
     */
    private SchedulerScenario determineSchedulerScenario(MainGraphState state) {
        // 检查是否有子图执行结果
        Optional<Map<String, String>> subgraphResults = state.getSubgraphResults();
        if (subgraphResults.isPresent() && !subgraphResults.get().isEmpty()) {
            // 有子图结果，说明是结果处理场景
            return SchedulerScenario.RESULT_PROCESSING;
        }
        
        // 没有子图结果，说明是初始调度场景
        return SchedulerScenario.INITIAL_SCHEDULING;
    }

    /**
     * 构建结果处理的用户提示词
     */
    private String buildResultProcessingUserPrompt(MainGraphState state) {
        String originalUserQuery = state.getOriginalUserQuery().orElse("用户查询");
        String todoListInfo = state.getTodoListForScheduler();
        String subgraphResultsInfo = buildSubgraphResultsInfo(state);
        
        return PromptTemplateManager.instance.buildSchedulerUpdateUserPrompt(
            originalUserQuery, todoListInfo, subgraphResultsInfo
        );
    }
    
    /**
     * 构建子图结果信息
     */
    private String buildSubgraphResultsInfo(MainGraphState state) {
        return state.getSubgraphResults()
                .map(results -> {
                    StringBuilder info = new StringBuilder();
                    results.forEach((agent, result) -> 
                        info.append(String.format("**%s执行结果**：\n%s\n\n", agent, result))
                    );
                    return info.toString();
                })
                .orElse("暂无子图执行结果");
    }

    /**
     * 处理任务状态更新
     */
    private Map<String, Object> processTaskUpdate(SchedulerResponse response, MainGraphState state) {
        SchedulerResponse.TaskUpdate taskUpdate = response.getTaskUpdate();
        SchedulerResponse.NextAction nextAction = response.getNextAction();
        
        // 更新TodoList中的任务状态
        updateTaskStatusInTodoList(state, taskUpdate);
        
        // 检查当前任务是否失败或重试且达到阈值
        if ("failed".equals(taskUpdate.getStatus()) || "retry".equals(taskUpdate.getStatus())) {
            TodoTask currentTask = state.getTodoList()
                    .map(todoList -> todoList.getTaskById(taskUpdate.getTaskId()))
                    .orElse(null);
            
            if (currentTask != null) {
                log.info("检查任务失败阈值: taskId={}, status={}, failureCount={}, taskUpdate.failureCount={}", 
                        currentTask.getTaskId(), taskUpdate.getStatus(), 
                        currentTask.getFailureCount(), taskUpdate.getFailureCount());
                
                if (currentTask.getFailureCount() >= 3) {
                    log.error("任务 {} 失败次数达到阈值: {}, 触发重新规划", 
                            currentTask.getTaskId(), currentTask.getFailureCount());
                    return createReplanRequestForFailedTask(state, currentTask, taskUpdate);
                } else {
                    log.info("任务 {} 失败次数未达到阈值: {}, 继续执行", 
                            currentTask.getTaskId(), currentTask.getFailureCount());
                }
            } else {
                log.warn("未找到任务: {}", taskUpdate.getTaskId());
            }
        }
        
        // 检查整体失败阈值（失败率检查）
        if (isFailureThresholdExceeded(state)) {
            log.error("整体任务失败率达到阈值，触发重新规划");
            return createReplanRequestForHighFailureRate(state, taskUpdate);
        }
        
        // 设置子图执行上下文
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("subgraphContext", nextAction.getContext());
        result.put("subgraphTaskDescription", nextAction.getTaskDescription());
        result.put("subgraphTaskId", taskUpdate.getTaskId());  // 设置当前任务的taskId
        
        // 关键：将更新后的TodoList放到返回值中，确保状态传递给下一个节点
        state.getTodoList().ifPresent(todoList -> {
            result.put("todoList", todoList);
            log.info("TodoList已添加到返回结果中，用于状态传递");
        });
        
        // 根据任务状态和下一步动作决定路由
        String nextNode = determineNextNode(taskUpdate, nextAction, state);
        result.put("next", nextNode);
        
        return result;
    }

    /**
     * 更新TodoList中的任务状态
     */
    private void updateTaskStatusInTodoList(MainGraphState state, SchedulerResponse.TaskUpdate taskUpdate) {
        Optional<TodoList> todoListOpt = state.getTodoList();
        if (todoListOpt.isPresent()) {
            TodoList todoList = todoListOpt.get();
            TodoTask task = todoList.getTaskById(taskUpdate.getTaskId());
            
            if (task != null) {
                // 更新任务状态
                switch (taskUpdate.getStatus()) {
                    case "in_progress" -> task.markInProgress();
                    case "completed" -> task.markCompleted();
                    case "failed" -> {
                        task.markFailed();
                        // 检查是否达到失败阈值
                        if (task.isFailedTooManyTimes()) {
                            log.warn("任务 {} 失败次数过多: {}", task.getTaskId(), task.getFailureCount());
                        }
                    }
                    case "retry" -> {
                        // 重试：递增失败计数，然后重置为pending状态
                        int oldFailureCount = task.getFailureCount();
                        task.setFailureCount(oldFailureCount + 1);
                        task.setStatus(TaskStatus.PENDING);
                        log.warn("任务 {} 需要重试，失败次数: {} -> {}", 
                                task.getTaskId(), oldFailureCount, task.getFailureCount());
                    }
                }
                
                // 更新失败计数（注意：retry情况下已经递增，不要覆盖）
                if (taskUpdate.getFailureCount() != null && !"retry".equals(taskUpdate.getStatus())) {
                    log.info("从taskUpdate更新失败计数: {} -> {}", task.getFailureCount(), taskUpdate.getFailureCount());
                    task.setFailureCount(taskUpdate.getFailureCount());
                } else {
                    log.info("保持当前失败计数: {} (taskUpdate.failureCount={}, status={})", 
                            task.getFailureCount(), taskUpdate.getFailureCount(), taskUpdate.getStatus());
                }
                
                log.info("更新任务状态: {} -> {}", task.getTaskId(), taskUpdate.getStatus());
                
                // 关键：将更新后的TodoList放回state，确保状态持久化
                state.setTodoList(todoList);
                log.info("TodoList状态已更新到MainGraphState");
            } else {
                log.warn("未找到任务: {}", taskUpdate.getTaskId());
            }
        }
    }

    /**
     * 确定下一个节点
     * 注意：失败阈值检查已在processTaskUpdate中提前处理
     */
    private String determineNextNode(SchedulerResponse.TaskUpdate taskUpdate, 
                                   SchedulerResponse.NextAction nextAction, 
                                   MainGraphState state) {
        String nextNode = nextAction.getNext();
        
        // 检查是否需要重新规划（Scheduler主动建议）
        if ("planner".equals(nextNode)) {
            log.warn("Scheduler主动建议重新规划，但没有创建重新规划消息，这可能导致问题");
            return "planner";
        }
        
        // 检查是否所有任务都已完成
        if ("summary".equals(nextNode)) {
            return "summary";
        }
        
        // 路由到子图或其他节点
        return nextNode;
    }

    /**
     * 检查是否达到失败阈值
     */
    private boolean isFailureThresholdExceeded(MainGraphState state) {
        return state.getTodoList()
                .map(todoList -> {
                    // 检查是否有任务失败次数过多
                    if (todoList.hasFailedTooManyTimes()) {
                        log.warn("TodoList中有任务失败次数过多，达到失败阈值");
                        return true;
                    }
                    
                    // 检查总体失败率
                    int totalTasks = todoList.getTasks().size();
                    int failedTasks = todoList.getFailedTasks().size();
                    
                    if (totalTasks > 0) {
                        double failureRate = (double) failedTasks / totalTasks;
                        double threshold = failureThresholdConfig != null ? 
                                failureThresholdConfig.getMaxFailures() / 10.0 : 0.5;
                        
                        if (failureRate >= threshold) {
                            log.warn("任务失败率过高: {}/{} ({}%), 达到失败阈值", 
                                    failedTasks, totalTasks, failureRate * 100);
                            return true;
                        }
                    }
                    
                    return false;
                })
                .orElse(false);
    }

    /**
     * 创建重新规划请求
     */
    private Map<String, Object> createReplanRequest(MainGraphState state, String reason, String detailedInfo) {
        String replanMessage = buildReplanMessage(reason, detailedInfo);
        UserMessage replanUserMessage = UserMessage.from(replanMessage);
        
        List<ChatMessage> messages = new ArrayList<>(state.messages());
        messages.add(replanUserMessage);
        
        log.info("创建重新规划请求，原因: {}", reason);
        
        // 创建返回结果
        Map<String, Object> result = new HashMap<>();
        result.put("messages", messages);
        result.put("next", "planner");
        
        // 确保TodoList状态也被传递
        state.getTodoList().ifPresent(todoList -> {
            result.put("todoList", todoList);
            log.info("TodoList已添加到重新规划请求中");
        });
        
        return result;
    }
    
    /**
     * 构建重新规划消息内容
     */
    private String buildReplanMessage(String reason, String detailedInfo) {
        StringBuilder message = new StringBuilder();
        message.append("【Scheduler建议重新规划】\n\n");
        message.append("原因：").append(reason).append("\n\n");
        
        if (detailedInfo != null && !detailedInfo.trim().isEmpty()) {
            message.append(detailedInfo).append("\n");
        }
        
        message.append("**重新规划建议**：\n");
        message.append("1. 分析任务失败的根本原因\n");
        message.append("2. 考虑是否需要拆分或调整任务策略\n");
        message.append("3. 可以删除无法完成的任务或添加辅助任务\n");
        message.append("4. 确保新的任务分配能够避免重复失败\n\n");
        message.append("请重新规划任务列表。");
        
        return message.toString();
    }
    
    /**
     * 为失败的单个任务创建重新规划请求
     */
    private Map<String, Object> createReplanRequestForFailedTask(MainGraphState state, TodoTask failedTask, SchedulerResponse.TaskUpdate taskUpdate) {
        String detailedInfo = buildFailedTaskDetails(failedTask, taskUpdate, state);
        return createReplanRequest(state, "任务失败次数过多", detailedInfo);
    }
    
    /**
     * 为高失败率创建重新规划请求
     */
    private Map<String, Object> createReplanRequestForHighFailureRate(MainGraphState state, SchedulerResponse.TaskUpdate taskUpdate) {
        TodoList todoList = state.getTodoList().orElse(null);
        if (todoList == null) {
            return createReplanRequest(state, "任务列表异常", null);
        }
        
        String detailedInfo = buildFailureRateDetails(todoList);
        return createReplanRequest(state, "任务整体失败率过高", detailedInfo);
    }
    
    /**
     * 创建简单的重新规划请求（fallback）
     */
    private Map<String, Object> createSimpleReplanRequest(MainGraphState state, String reason) {
        return createReplanRequest(state, reason, null);
    }
    
    /**
     * 构建失败任务详情
     */
    private String buildFailedTaskDetails(TodoTask failedTask, SchedulerResponse.TaskUpdate taskUpdate, MainGraphState state) {
        StringBuilder details = new StringBuilder();
        details.append("**失败任务详情**：\n");
        details.append(String.format("- 任务ID: %s\n", failedTask.getTaskId()));
        details.append(String.format("- 任务描述: %s\n", failedTask.getDescription()));
        details.append(String.format("- 分配智能体: %s\n", failedTask.getAssignedAgent()));
        details.append(String.format("- 失败次数: %d次\n", failedTask.getFailureCount()));
        details.append(String.format("- 最后失败原因: %s\n\n", taskUpdate.getReason()));
        
        addRelatedSubgraphHistory(details, state, failedTask.getAssignedAgent());
        return details.toString();
    }
    
    /**
     * 构建失败率详情
     */
    private String buildFailureRateDetails(TodoList todoList) {
        int totalTasks = todoList.getTasks().size();
        int failedTasks = todoList.getFailedTasks().size();
        double failureRate = (double) failedTasks / totalTasks;
        
        StringBuilder details = new StringBuilder();
        details.append("**失败统计**：\n");
        details.append(String.format("- 总任务数: %d\n", totalTasks));
        details.append(String.format("- 失败任务数: %d\n", failedTasks));
        details.append(String.format("- 失败率: %.1f%%\n\n", failureRate * 100));
        
        if (!todoList.getFailedTasks().isEmpty()) {
            details.append("**失败任务列表**：\n");
            todoList.getFailedTasks().forEach(task -> 
                details.append(String.format("- [%s] %s (智能体: %s, 失败次数: %d)\n", 
                        task.getTaskId(), task.getDescription(), 
                        task.getAssignedAgent(), task.getFailureCount()))
            );
            details.append("\n");
        }
        
        return details.toString();
    }
    
    /**
     * 添加相关子图执行历史
     */
    private void addRelatedSubgraphHistory(StringBuilder details, MainGraphState state, String assignedAgent) {
        Optional<Map<String, String>> subgraphResults = state.getSubgraphResults();
        if (subgraphResults.isPresent() && !subgraphResults.get().isEmpty()) {
            details.append("**相关子图执行历史**：\n");
            subgraphResults.get().forEach((agent, result) -> {
                if (agent.contains(assignedAgent)) {
                    String truncatedResult = result.length() > 200 ? 
                            result.substring(0, 200) + "..." : result;
                    details.append(String.format("- %s: %s\n", agent, truncatedResult));
                }
            });
            details.append("\n");
        }
    }

    /**
     * 重写错误结果创建方法
     * Scheduler解析失败时，应该重新尝试调度，而不是继续执行
     */
    @Override
    protected Map<String, Object> createErrorResult(MainGraphState state, String errorMessage) {
        log.error("Scheduler错误: {}, 将重新尝试调度", errorMessage);
        
        // 创建包含错误信息的消息
        List<ChatMessage> errorMessages = new ArrayList<>(state.messages());
        errorMessages.add(UserMessage.from("上一次调度失败: " + errorMessage + "。请重新分析并输出正确的调度结果。"));
        
        return Map.of(
                "messages", errorMessages,
                "next", "scheduler"  // 重新尝试调度
        );
    }

    /**
     * 调度场景枚举
     */
    private enum SchedulerScenario {
        INITIAL_SCHEDULING,  // 初始调度：从TodoListParser接收任务
        RESULT_PROCESSING    // 结果处理：处理子图返回结果
    }
}