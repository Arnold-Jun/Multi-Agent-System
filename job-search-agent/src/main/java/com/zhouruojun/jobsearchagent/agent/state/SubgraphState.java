package com.zhouruojun.jobsearchagent.agent.state;


import java.lang.reflect.Field;
import java.util.Map;
import java.util.Optional;

/**
 * 子图状态类
 * 包含子图执行所需的最小状态信息
 * 用于子图智能体执行和工具调用
 */
public class SubgraphState extends BaseAgentState {

    public SubgraphState(Map<String, Object> initData) {
        super(initData);
    }

    /**
     * 获取工具执行结果
     */
    public Optional<String> getToolExecutionResult() {
        return this.value("toolExecutionResult");
    }

    /**
     * 获取子图智能体的最终响应
     */
    public Optional<String> getFinalResponse() {
        return this.value("finalResponse");
    }

    /**
     * 获取子图执行状态
     */
    public Optional<String> getExecutionStatus() {
        return this.value("executionStatus");
    }

    /**
     * 获取当前任务
     */
    public Optional<com.zhouruojun.jobsearchagent.agent.todo.TodoTask> getCurrentTask() {
        return this.value("currentTask");
    }

    /**
     * 获取子图类型
     */
    public Optional<String> getSubgraphType() {
        return this.value("subgraphType");
    }

    /**
     * 获取子图上下文信息（来自Scheduler）
     */
    public Optional<String> getSubgraphContext() {
        return this.value("subgraphContext");
    }

    /**
     * 获取子图任务描述（来自Scheduler）
     */
    public Optional<String> getSubgraphTaskDescription() {
        return this.value("subgraphTaskDescription");
    }

    // === 状态更新方法 ===

    /**
     * 更新工具执行结果 - 使用Builder模式
     */
    public SubgraphState withToolExecutionResult(String result) {
        return withData("toolExecutionResult", result, SubgraphState.class);
    }

    /**
     * 更新最终响应 - 使用Builder模式
     */
    public SubgraphState withFinalResponse(String finalResponse) {
        return withData("finalResponse", finalResponse, SubgraphState.class);
    }

    /**
     * 更新执行状态 - 使用Builder模式
     */
    public SubgraphState withExecutionStatus(String status) {
        return withData("executionStatus", status, SubgraphState.class);
    }


}