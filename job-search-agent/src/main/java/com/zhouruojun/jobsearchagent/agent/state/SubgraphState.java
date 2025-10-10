package com.zhouruojun.jobsearchagent.agent.state;

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

}