package com.zhouruojun.jobsearchagent.agent.state;

import com.zhouruojun.jobsearchagent.agent.state.main.TodoList;
import com.zhouruojun.jobsearchagent.agent.state.main.TodoTask;
import dev.langchain4j.data.message.ChatMessage;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 主图状态类
 * 包含主图运行时的所有状态信息
 */
@Slf4j
public class MainGraphState extends BaseAgentState {

    private final Map<String, Object> state;
    private final List<ChatMessage> messages;

    public MainGraphState(Map<String, Object> state) {
        super(state);
        this.state = new HashMap<>(state);
        this.messages = extractMessages(state);
    }

    public MainGraphState() {
        super(new HashMap<>());
        this.state = new HashMap<>();
        this.messages = new ArrayList<>();
    }

    @Override
    public List<ChatMessage> messages() {
        return messages;
    }

    @Override
    public Optional<ChatMessage> lastMessage() {
        return messages.isEmpty() ? Optional.empty() : Optional.of(messages.get(messages.size() - 1));
    }

    public Optional<Object> getValue(String key) {
        return Optional.ofNullable(state.get(key));
    }

    /**
     * 获取重规划次数
     */
    public int getReplanCount() {
        return (Integer) state.getOrDefault("replanCount", 0);
    }

    /**
     * 设置重规划次数
     */
    public void setReplanCount(int count) {
        state.put("replanCount", count);
    }

    /**
     * 增加重规划次数
     */
    public void incrementReplanCount() {
        int currentCount = getReplanCount();
        setReplanCount(currentCount + 1);
        log.info("重规划次数增加: {} -> {}", currentCount, currentCount + 1);
    }

    /**
     * 检查是否超过重规划限制
     */
    public boolean isReplanLimitExceeded() {
        return getReplanCount() >= 1; // 限制为1次重规划
    }

    public void addMessage(ChatMessage message) {
        messages.add(message);
        state.put("messages", messages);
    }

    public void setTodoList(TodoList todoList) {
        state.put("todoList", todoList);
    }

    public Optional<TodoList> getTodoList() {
        return Optional.ofNullable((TodoList) state.get("todoList"));
    }

    public void setFinalResponse(String finalResponse) {
        state.put("finalResponse", finalResponse);
    }

    public Optional<String> getFinalResponse() {
        return Optional.ofNullable((String) state.get("finalResponse"));
    }

    public void setAnalysisResult(String analysisResult) {
        state.put("analysisResult", analysisResult);
    }

    public Optional<String> getAnalysisResult() {
        return Optional.ofNullable((String) state.get("analysisResult"));
    }



    /**
     * 获取子图结果映射
     */
    @SuppressWarnings("unchecked")
    public Optional<Map<String, String>> getSubgraphResults() {
        return Optional.ofNullable((Map<String, String>) state.get("subgraphResults"));
    }



    /**
     * 获取Todo列表统计信息
     */
    public String getTodoListStatistics() {
        return getTodoList()
                .map(TodoList::getStatistics)
                .orElse("无任务列表");
    }

    /**
     * 获取当前任务的上下文信息（用于初始调度场景）
     */
    public String getCurrentTaskContext() {
        return getTodoList()
                .map(todoList -> {
                    TodoTask currentTask = todoList.getNextExecutableTask();
                    if (currentTask == null) {
                        return "没有可执行的任务";
                    }
                    
                    StringBuilder info = new StringBuilder();
                    info.append("=== 当前执行任务 ===\n");
                    info.append(String.format("任务ID: %s\n", currentTask.getTaskId()));
                    info.append(String.format("任务描述: %s\n", currentTask.getDescription()));
                    info.append(String.format("分配智能体: %s\n", currentTask.getAssignedAgent()));
                    info.append(String.format("任务状态: %s\n", currentTask.getStatus().getDescription()));
                    info.append(String.format("失败次数: %d\n", currentTask.getFailureCount()));
                    info.append(String.format("任务顺序: %d\n", currentTask.getOrder()));
                    
                    // 检查是否有下一个待执行任务
                    List<TodoTask> pendingTasks = todoList.getPendingTasks();
                    TodoTask nextTask = pendingTasks.stream()
                            .filter(task -> !task.getTaskId().equals(currentTask.getTaskId()))
                            .findFirst()
                            .orElse(null);
                    
                    if (nextTask != null) {
                        info.append("\n=== 下一个待执行任务 ===\n");
                        info.append(String.format("任务ID: %s\n", nextTask.getTaskId()));
                        info.append(String.format("任务描述: %s\n", nextTask.getDescription()));
                        info.append(String.format("分配智能体: %s\n", nextTask.getAssignedAgent()));
                        if (nextTask.getFailureCount() > 0) {
                            info.append(String.format("失败次数: %d\n", nextTask.getFailureCount()));
                        }
                        info.append(String.format("任务顺序: %d\n", nextTask.getOrder()));
                    } else {
                        info.append("\n=== 后续任务状态 ===\n");
                        info.append("当前任务是最后一个待执行任务，没有后续任务\n");
                    }
                    
                    return info.toString();
                })
                .orElse("无任务列表");
    }

    /**
     * 获取为Scheduler优化的TodoList详细信息
     * 包含任务统计、待执行任务详情、执行中任务状态等关键信息
     */
    public String getTodoListForScheduler() {
        return getTodoList()
                .map(todoList -> {
                    StringBuilder info = new StringBuilder();
                    info.append("=== 任务列表状态 ===\n");
                    info.append(todoList.getStatistics()).append("\n\n");
                    
                    // 重点显示待执行任务
                    if (!todoList.getPendingTasks().isEmpty()) {
                        info.append("=== 待执行任务 ===\n");
                        for (TodoTask task : todoList.getPendingTasks()) {
                            if (task.getFailureCount() > 0) {
                                info.append(String.format("- [%s] %s (智能体: %s, 失败%d次)\n", 
                                    task.getTaskId(), task.getDescription(), task.getAssignedAgent(), task.getFailureCount()));
                            } else {
                                info.append(String.format("- [%s] %s (智能体: %s)\n", 
                                    task.getTaskId(), task.getDescription(), task.getAssignedAgent()));
                            }
                        }
                        info.append("\n");
                    }
                    
                    // 显示执行中任务
                    if (!todoList.getInProgressTasks().isEmpty()) {
                        info.append("=== 执行中任务 ===\n");
                        for (TodoTask task : todoList.getInProgressTasks()) {
                            info.append(String.format("- [%s] %s (智能体: %s)\n", 
                                task.getTaskId(), task.getDescription(), task.getAssignedAgent()));
                        }
                        info.append("\n");
                    }
                    
                    // 显示已完成任务（简要）
                    if (!todoList.getCompletedTasks().isEmpty()) {
                        info.append("=== 已完成任务 ===\n");
                        for (TodoTask task : todoList.getCompletedTasks()) {
                            info.append(String.format("- [%s] %s\n", 
                                task.getTaskId(), task.getDescription()));
                        }
                        info.append("\n");
                    }
                    
                    // 显示失败任务（简要）
                    if (!todoList.getFailedTasks().isEmpty()) {
                        info.append("=== 失败任务 ===\n");
                        for (TodoTask task : todoList.getFailedTasks()) {
                            info.append(String.format("- [%s] %s (失败次数: %d)\n", 
                                task.getTaskId(), task.getDescription(), task.getFailureCount()));
                        }
                        info.append("\n");
                    }
                    
                    return info.toString();
                })
                .orElse("无任务列表");
    }

    
    /**
     * 更新子图结果 - 使用Builder模式
     */
    public MainGraphState withSubgraphResult(String agentName, String result) {
        Map<String, String> results = getSubgraphResults().orElse(new java.util.HashMap<>());
        results.put(agentName, result);
        MainGraphState newState = new MainGraphState(new java.util.HashMap<>(state));
        newState.state.put("subgraphResults", results);
        return newState;
    }

    
    /**
     * 获取原始用户查询
     */
    public Optional<String> getOriginalUserQuery() {
        return Optional.ofNullable((String) state.get("originalUserQuery"));
    }
    

    private List<ChatMessage> extractMessages(Map<String, Object> state) {
        Object messagesObj = state.get("messages");
        if (messagesObj instanceof List) {
            return ((List<?>) messagesObj).stream()
                    .filter(o -> o instanceof ChatMessage)
                    .map(o -> (ChatMessage) o)
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }

}