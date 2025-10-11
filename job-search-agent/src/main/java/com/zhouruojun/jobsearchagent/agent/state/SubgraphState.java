package com.zhouruojun.jobsearchagent.agent.state;

import com.zhouruojun.jobsearchagent.agent.state.main.TodoTask;
import com.zhouruojun.jobsearchagent.agent.state.subgraph.ToolExecutionHistory;

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
     * 获取工具执行历史管理对象
     */
    public Optional<ToolExecutionHistory> getToolExecutionHistory() {
        return this.value("toolExecutionHistory");
    }

    /**
     * 获取工具执行历史管理对象（非Optional版本）
     * 提供类型安全的访问，如果不存在或类型不匹配则抛异常
     * 
     * @return 工具执行历史管理对象
     * @throws IllegalStateException 如果历史对象不存在或类型不匹配
     */
    public ToolExecutionHistory getToolExecutionHistoryOrThrow() {
        return this.value("toolExecutionHistory")
            .map(obj -> {
                if (obj instanceof ToolExecutionHistory) {
                    return (ToolExecutionHistory) obj;
                }
                throw new IllegalStateException(
                    String.format("toolExecutionHistory类型不匹配，期望: ToolExecutionHistory, 实际: %s", 
                        obj.getClass().getName())
                );
            })
            .orElseThrow(() -> new IllegalStateException(
                "toolExecutionHistory未初始化，请确保在子图初始化时创建该对象"
            ));
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
        return this.value("subgraphType");
    }

    /**
     * 获取子图上下文信息（来自Scheduler）
     */
    public Optional<String> getSubgraphContext() {
        return this.value("subgraphContext");
    }

}