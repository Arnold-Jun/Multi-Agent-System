package com.zhouruojun.travelingagent.agent.actions;

import com.zhouruojun.travelingagent.agent.BaseAgent;
import com.zhouruojun.travelingagent.agent.dto.SchedulerResponse;
import com.zhouruojun.travelingagent.agent.parser.SchedulerResponseParser;
import com.zhouruojun.travelingagent.agent.state.MainGraphState;
import com.zhouruojun.travelingagent.agent.state.main.TodoList;
import com.zhouruojun.travelingagent.agent.state.main.TodoTask;
import com.zhouruojun.travelingagent.agent.state.main.TaskStatus;
import com.zhouruojun.travelingagent.common.PromptTemplateManager;
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

    /**
     * 构造函数
     *
     * @param agentName 智能体名称
     * @param agent 智能体实例
     * @param schedulerResponseParser 调度响应解析器
     */
    public CallSchedulerAgent(@NonNull String agentName, 
                             @NonNull BaseAgent agent,
                             @NonNull SchedulerResponseParser schedulerResponseParser) {
        super(agentName, agent);
        this.schedulerResponseParser = schedulerResponseParser;
    }

    @Override
    protected List<ChatMessage> buildMessages(MainGraphState state) {
        SchedulerScenario scenario = determineSchedulerScenario(state);
        
        List<ChatMessage> messages = buildMessagesForScenario(scenario, state);
        
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
                        state.getOriginalUserQuery().orElse("请帮我调度旅游任务"),
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
            case USER_INPUT_PROCESSING -> {
                addSchedulerMessages(messages,
                    PromptTemplateManager.instance.buildSchedulerUpdateSystemPrompt(),
                    buildUserInputProcessingUserPrompt(state)
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
            SchedulerResponse response = schedulerResponseParser.parseResponse(filteredMessage.text());
            
            // 处理任务状态更新
            Map<String, Object> result = processTaskUpdate(response, state);

            if (!result.containsKey("messages")) {
                result.put("messages", List.of(filteredMessage));
            }
            
            log.info("调度智能体 {} 完成调度，任务ID: {}, 状态: {}, 下一步: {}", 
                    agentName, 
                    response.getTaskUpdate().getTaskId(),
                    response.getTaskUpdate().getStatus(),
                    result.get("next"));  // 使用result中的next，而不是response中的next
            
            return result;
            
        } catch (Exception e) {
            log.error("调度智能体 {} 处理响应失败: {}", agentName, e.getMessage(), e);
            
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
        // 检查是否有用户输入（从userInput节点恢复）
        if (state.getValue("userInput").isPresent()) {
            // 有用户输入，说明是从userInput节点恢复，需要处理用户输入
            return SchedulerScenario.USER_INPUT_PROCESSING;
        }
        
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
     * 构建用户输入处理的用户提示词
     */
    private String buildUserInputProcessingUserPrompt(MainGraphState state) {
        String originalUserQuery = state.getOriginalUserQuery().orElse("用户查询");
        String userInput = (String) state.getValue("userInput").orElse("用户输入");
        String todoListInfo = state.getTodoListForScheduler();
        
        // 构建用户输入历史
        List<String> userInputHistory = state.getUserQueryHistory();
        String userInputHistoryStr = buildUserInputHistoryString(userInputHistory);
        
        return PromptTemplateManager.instance.buildSchedulerUserInputUserPrompt(
            originalUserQuery, userInput, userInputHistoryStr, todoListInfo
        );
    }
    
    /**
     * 构建用户输入历史字符串
     */
    private String buildUserInputHistoryString(List<String> userInputHistory) {
        if (userInputHistory == null || userInputHistory.isEmpty()) {
            return "无历史输入";
        }
        
        StringBuilder historyBuilder = new StringBuilder();
        for (int i = 0; i < userInputHistory.size(); i++) {
            historyBuilder.append(String.format("第%d轮输入：%s\n", i + 1, userInputHistory.get(i)));
        }
        return historyBuilder.toString().trim();
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
        
        // 问号启发式：如果finalResponse以问号结尾，直接路由到userInput
        String finalResponse = state.getFinalResponse().orElse(null);
        if (finalResponse != null && endsWithQuestionMark(finalResponse)) {
            log.info("问号启发式触发：finalResponse以问号结尾，直接路由到userInput");
            
            Map<String, Object> result = new HashMap<>();
            result.put("finalResponse", finalResponse);
            result.put("userInputRequired", true);
            
            // 添加通用状态信息
            addCommonStateInfo(result, state);
            result.put("next", "userInput");
            
            return result;
        }
        
        // 检查Scheduler是否建议重规划
        if ("planner".equals(nextAction.getNext())) {
            // 检查重规划限制
            if (state.isReplanLimitExceeded()) {
                log.warn("重规划次数已达限制 ({}), 强制路由到summary", state.getReplanCount());
                return createSummaryRequest(state, "重规划次数已达限制，生成最终报告");
            }
            
            TodoTask currentTask = state.getTodoList()
                    .map(todoList -> todoList.getTaskById(taskUpdate.getTaskId()))
                    .orElse(null);
            
            if (currentTask != null) {
                log.info("Scheduler建议重规划: taskId={}, status={}, failureCount={}, 当前重规划次数={}", 
                        currentTask.getTaskId(), taskUpdate.getStatus(), currentTask.getFailureCount(), state.getReplanCount());
                
                // 直接信任Scheduler的判断，使用Scheduler提供的上下文
                state.incrementReplanCount();
                return createReplanRequestWithSchedulerContext(state, nextAction, taskUpdate, currentTask);
            } else {
                log.warn("未找到任务: {}, 强制重规划", taskUpdate.getTaskId());
                state.incrementReplanCount();
                return createReplanRequest(state, "任务不存在，需要重新规划", null);
            }
        }
        
        // 兜底检查：如果Scheduler没有建议重规划，但任务失败次数过多，强制重规划
        if (!"planner".equals(nextAction.getNext()) && !"summary".equals(nextAction.getNext())) {
            TodoTask currentTask = state.getTodoList()
                    .map(todoList -> todoList.getTaskById(taskUpdate.getTaskId()))
                    .orElse(null);
            
            if (currentTask != null && currentTask.getFailureCount() >= 3) {
                // 检查重规划限制
                if (state.isReplanLimitExceeded()) {
                    log.warn("兜底检查：任务 {} 失败次数达到阈值: {}, 但重规划次数已达限制 ({}), 强制路由到summary", 
                            currentTask.getTaskId(), currentTask.getFailureCount(), state.getReplanCount());
                    return createSummaryRequest(state, "任务失败次数过多且重规划次数已达限制，生成最终报告");
                }
                
                log.error("兜底检查：任务 {} 失败次数达到阈值: {}, 但Scheduler未建议重规划，强制重规划", 
                        currentTask.getTaskId(), currentTask.getFailureCount());
                // 增加重规划次数
                state.incrementReplanCount();
                return createReplanRequestForFailedTask(state, currentTask, taskUpdate);
            }
        }
        
        // 验证next字段
        String nextNode = nextAction.getNext();
        if (nextNode == null || nextNode.trim().isEmpty()) {
            log.error("Scheduler返回的next字段为空，taskId: {}, status: {}", 
                    taskUpdate.getTaskId(), taskUpdate.getStatus());
            throw new IllegalStateException("CallSchedulerAgent必须设置有效的next字段");
        }
        
        // 设置子图执行上下文
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("subgraphContext", nextAction.getContext());
        result.put("subgraphTaskDescription", nextAction.getTaskDescription());
        result.put("subgraphTaskId", taskUpdate.getTaskId());  // 设置当前任务的taskId
        
        // 添加通用状态信息
        addCommonStateInfo(result, state);
        // 使用验证后的next值
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
                    case "failed" -> task.markFailed();
                    case "retry" -> {
                        // 直接信任Scheduler的retry判断
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
                
                // 如果当前任务完成，自动将下一个可执行任务标记为in_progress
                if ("completed".equals(taskUpdate.getStatus())) {
                    markNextTaskAsInProgress(todoList);
                }
                
                // 将更新后的TodoList放回state，确保状态持久化
                state.setTodoList(todoList);
                log.info("TodoList状态已更新到MainGraphState");
            } else {
                log.warn("未找到任务: {}", taskUpdate.getTaskId());
            }
        }
    }

    /**
     * 将下一个可执行任务标记为in_progress
     */
    private void markNextTaskAsInProgress(TodoList todoList) {
        TodoTask nextTask = todoList.getNextExecutableTask();
        if (nextTask != null) {
            nextTask.markInProgress();
            log.info("自动将下一个任务 {} 标记为in_progress", nextTask.getTaskId());
        } else {
            log.info("没有找到下一个可执行任务");
        }
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
        
        // 添加通用状态信息
        addCommonStateInfo(result, state);
        
        return result;
    }
    
    /**
     * 创建Summary请求
     */
    private Map<String, Object> createSummaryRequest(MainGraphState state, String reason) {
        String summaryMessage = String.format("""
        **系统限制触发**：
        %s
        
        **当前状态**：
        - 重规划次数：%d
        - 任务状态：请查看TodoList获取详细信息
        
        **要求**：
        请基于当前所有可用的信息生成最终的用户回复，包括：
        1. 已完成任务的总结
        2. 失败任务的分析
        3. 对用户的建议和下一步行动
        4. 整体旅游策略的评估
        """, reason, state.getReplanCount());
        
        List<ChatMessage> messages = new ArrayList<>(state.messages());
        messages.add(UserMessage.from(summaryMessage));
        
        Map<String, Object> result = new HashMap<>();
        result.put("messages", messages);
        result.put("next", "summary");
        
        // 添加通用状态信息
        addCommonStateInfo(result, state);
        
        return result;
    }
    
    /**
     * 添加通用状态信息到结果中
     */
    private void addCommonStateInfo(Map<String, Object> result, MainGraphState state) {
        // 添加TodoList状态
        state.getTodoList().ifPresent(todoList -> {
            result.put("todoList", todoList);
        });
        
        // 添加重规划计数
        result.put("replanCount", state.getReplanCount());
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
        message.append("3. 可以添加新任务来解决之前的问题\n");
        message.append("4. 保留失败任务的历史记录，通过modify操作更新状态\n");
        message.append("5. 确保新的任务分配能够避免重复失败\n\n");
        message.append("请重新规划任务列表。");
        
        return message.toString();
    }

    /**
     * 使用Scheduler提供的上下文创建重规划请求
     */
    private Map<String, Object> createReplanRequestWithSchedulerContext(MainGraphState state, 
                                                                        SchedulerResponse.NextAction nextAction, 
                                                                        SchedulerResponse.TaskUpdate taskUpdate,
                                                                        TodoTask task) {
        // 使用Scheduler提供的taskDescription和context
        String reason = taskUpdate.getReason() != null ? taskUpdate.getReason() : "Scheduler建议重新规划";
        String detailedInfo = buildTaskDetails(task, taskUpdate, nextAction, state, true);
        
        return createReplanRequest(state, reason, detailedInfo);
    }
    
    /**
     * 为失败的单个任务创建重新规划请求（兜底方案）
     */
    private Map<String, Object> createReplanRequestForFailedTask(MainGraphState state, TodoTask failedTask, SchedulerResponse.TaskUpdate taskUpdate) {
        String detailedInfo = buildTaskDetails(failedTask, taskUpdate, null, state, false);
        
        // 使用taskUpdate中的reason作为主原因，如果没有则使用默认原因
        String reason = taskUpdate.getReason() != null && !taskUpdate.getReason().trim().isEmpty() 
                ? taskUpdate.getReason() 
                : "任务失败次数过多";
                
        return createReplanRequest(state, reason, detailedInfo);
    }

    /**
     * 构建任务详情（通用方法）
     */
    private String buildTaskDetails(TodoTask task, SchedulerResponse.TaskUpdate taskUpdate, 
                                   SchedulerResponse.NextAction nextAction, MainGraphState state, 
                                   boolean includeSchedulerContext) {
        StringBuilder details = new StringBuilder();
        
        if (includeSchedulerContext) {
            details.append("**Scheduler建议重规划详情**：\n");
        } else {
            details.append("**失败任务详情**：\n");
        }
        
        details.append(String.format("- 任务ID: %s\n", task.getTaskId()));
        details.append(String.format("- 任务描述: %s\n", task.getDescription()));
        details.append(String.format("- 分配智能体: %s\n", task.getAssignedAgent()));
        details.append(String.format("- 失败次数: %d次\n", task.getFailureCount()));
        details.append(String.format("- 失败原因: %s\n", taskUpdate.getReason()));
        
        if (includeSchedulerContext && nextAction != null) {
            details.append(String.format("- Scheduler建议的新任务描述: %s\n", nextAction.getTaskDescription()));
            details.append(String.format("- Scheduler提供的上下文: %s\n", nextAction.getContext()));
        }
        
        details.append("\n");
        addRelatedSubgraphHistory(details, state, task.getAssignedAgent());
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
     * 调度场景枚举
     */
    private enum SchedulerScenario {
        INITIAL_SCHEDULING,    // 初始调度：从TodoListParser接收任务
        RESULT_PROCESSING,     // 结果处理：处理子图返回结果
        USER_INPUT_PROCESSING  // 用户输入处理：处理从userInput节点恢复的用户输入
    }
}