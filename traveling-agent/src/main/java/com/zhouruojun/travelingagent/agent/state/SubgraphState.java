package com.zhouruojun.travelingagent.agent.state;

import com.zhouruojun.travelingagent.agent.state.main.TodoTask;
import com.zhouruojun.travelingagent.agent.state.subgraph.ToolExecutionHistory;
import dev.langchain4j.data.message.ChatMessage;

import java.util.*;

/**
 * 子图状态类
 * 管理子图执行过程中的状态
 */
public class SubgraphState extends BaseAgentState {

    private final Map<String, Object> state;
    private final List<ChatMessage> messages;

    public SubgraphState(Map<String, Object> state) {
        super(state);
        this.state = new HashMap<>(state);
        this.messages = extractMessages(state);
    }

    public SubgraphState() {
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
     * 获取工具执行结果
     */
    public Optional<String> getToolExecutionResult() {
        return Optional.ofNullable((String) state.get("toolExecutionResult"));
    }

    /**
     * 设置工具执行结果
     */
    public void setToolExecutionResult(String result) {
        state.put("toolExecutionResult", result);
    }

    /**
     * 获取工具执行历史
     */
    public ToolExecutionHistory getToolExecutionHistory() {
        ToolExecutionHistory history = (ToolExecutionHistory) state.get("toolExecutionHistory");
        if (history == null) {
            history = new ToolExecutionHistory();
            state.put("toolExecutionHistory", history);
        }
        return history;
    }

    /**
     * 设置工具执行历史
     */
    public void setToolExecutionHistory(ToolExecutionHistory history) {
        state.put("toolExecutionHistory", history);
    }

    /**
     * 获取工具执行历史管理对象（非Optional版本）
     * 提供类型安全的访问，如果不存在或类型不匹配则抛异常
     * 
     * @return 工具执行历史管理对象
     * @throws IllegalStateException 如果历史对象不存在或类型不匹配
     */
    public ToolExecutionHistory getToolExecutionHistoryOrThrow() {
        Object obj = state.get("toolExecutionHistory");
        if (obj instanceof ToolExecutionHistory) {
            return (ToolExecutionHistory) obj;
        }
        throw new IllegalStateException(
            "toolExecutionHistory未初始化，请确保在子图初始化时创建该对象"
        );
    }

    /**
     * 获取当前任务
     */
    public Optional<TodoTask> getCurrentTask() {
        return this.value("currentTask");
    }

    /**
     * 设置当前任务
     */
    public void setCurrentTask(TodoTask task) {
        state.put("currentTask", task);
    }

    /**
     * 获取子图类型
     */
    public Optional<String> getSubgraphType() {
        return Optional.ofNullable((String) state.get("subgraphType"));
    }

    /**
     * 设置子图类型
     */
    public void setSubgraphType(String type) {
        state.put("subgraphType", type);
    }

    /**
     * 获取子图上下文
     */
    public Optional<String> getSubgraphContext() {
        return Optional.ofNullable((String) state.get("subgraphContext"));
    }

    /**
     * 设置子图上下文
     */
    public void setSubgraphContext(String context) {
        state.put("subgraphContext", context);
    }



    /**
     * 检查是否有工具执行结果
     */
    public boolean hasToolExecutionResult() {
        return getToolExecutionResult().isPresent();
    }

    /**
     * 检查是否有工具执行历史
     */
    public boolean hasToolExecutionHistory() {
        return getToolExecutionHistory() != null && getToolExecutionHistory().getTotalCount() > 0;
    }

    private List<ChatMessage> extractMessages(Map<String, Object> state) {
        Object messagesObj = state.get("messages");
        if (messagesObj instanceof List) {
            return ((List<?>) messagesObj).stream()
                    .filter(o -> o instanceof ChatMessage)
                    .map(o -> (ChatMessage) o)
                    .collect(java.util.stream.Collectors.toList());
        }
        return new ArrayList<>();
    }
}
