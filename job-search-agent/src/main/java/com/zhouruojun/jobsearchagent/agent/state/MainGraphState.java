package com.zhouruojun.jobsearchagent.agent.state;

import com.zhouruojun.jobsearchagent.agent.todo.TodoList;
import com.zhouruojun.jobsearchagent.agent.todo.TodoTask;
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

    public Map<String, Object> getValues() {
        return state;
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
     * 获取指定子图的结果
     */
    public Optional<String> getSubgraphResult(String agentName) {
        return getSubgraphResults()
                .map(results -> results.get(agentName));
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
                            info.append(String.format("- [%s] %s (智能体: %s)\n", 
                                task.getTaskId(), task.getDescription(), task.getAssignedAgent()));
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
     * 更新TodoList状态（兼容性方法）
     */
    public void updateTodoList(TodoList todoList) {
        // 确保todoList不为null
        if (todoList == null) {
            log.warn("尝试设置null的TodoList，创建空的TodoList");
            todoList = new TodoList();
        }
        state.put("todoList", todoList);
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
     * 更新TodoList - 使用Builder模式
     */
    public MainGraphState withTodoList(TodoList todoList) {
        MainGraphState newState = new MainGraphState(new java.util.HashMap<>(state));
        newState.state.put("todoList", todoList);
        return newState;
    }
    
    /**
     * 获取原始用户查询
     */
    public Optional<String> getOriginalUserQuery() {
        return Optional.ofNullable((String) state.get("originalUserQuery"));
    }
    
    /**
     * 获取子图执行上下文（由Scheduler设置）
     * context包含详细的执行指令和要求
     */
    public Optional<String> getSubgraphContext() {
        return Optional.ofNullable((String) state.get("subgraphContext"));
    }
    
    /**
     * 获取子图任务描述（由Scheduler设置）
     * taskDescription是任务的概要描述
     */
    public Optional<String> getSubgraphTaskDescription() {
        return Optional.ofNullable((String) state.get("subgraphTaskDescription"));
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