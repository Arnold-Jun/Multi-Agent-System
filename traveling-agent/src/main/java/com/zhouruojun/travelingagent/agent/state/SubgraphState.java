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
     * 获取工具执行历史
     */
    public ToolExecutionHistory getToolExecutionHistory() {
        ToolExecutionHistory history = (ToolExecutionHistory) state.get("toolExecutionHistory");
        if (history == null) {
            // 返回一个新的实例，但不修改state（由节点返回的Map来更新state）
            return new ToolExecutionHistory();
        }
        return history;
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
     * 获取子图类型
     */
    public Optional<String> getSubgraphType() {
        return Optional.ofNullable((String) state.get("subgraphType"));
    }

    /**
     * 获取子图上下文
     */
    public Optional<String> getSubgraphContext() {
        return Optional.ofNullable((String) state.get("subgraphContext"));
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

    /**
     * 获取当前完整方案（由itineraryPlanner生成）
     */
    public Optional<String> getCurrentPlan() {
        return Optional.ofNullable((String) state.get("currentPlan"));
    }

    /**
     * 获取行程规划方案（由plannerAgent生成，已废弃，保留用于兼容）
     * @deprecated 使用 getCurrentPlan() 获取完整方案
     */
    @Deprecated
    public Optional<String> getItineraryPlan() {
        return Optional.ofNullable((String) state.get("itineraryPlan"));
    }

    /**
     * 获取机酒规划方案（由plannerAgent生成，已废弃，保留用于兼容）
     * @deprecated 使用 getCurrentPlan() 获取完整方案
     */
    @Deprecated
    public Optional<String> getTransportHotelPlan() {
        return Optional.ofNullable((String) state.get("transportHotelPlan"));
    }

    /**
     * 获取监督反馈（由supervisorAgent生成）
     */
    public Optional<String> getSupervisorFeedback() {
        return Optional.ofNullable((String) state.get("supervisorFeedback"));
    }

    /**
     * 获取迭代次数
     */
    public int getIterationCount() {
        Object count = state.get("iterationCount");
        if (count instanceof Integer) {
            return (Integer) count;
        }
        return 0;
    }

    /**
     * 检查是否需要修改方案（由supervisorAgent设置）
     */
    public boolean needsRevision() {
        Object needs = state.get("needsRevision");
        return needs instanceof Boolean && (Boolean) needs;
    }

    /**
     * 检查是否达到最大迭代次数
     */
    public boolean isMaxIterationsReached() {
        return getIterationCount() >= 5; // 最大5次迭代
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
