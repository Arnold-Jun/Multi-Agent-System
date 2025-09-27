package com.zhouruojun.dataanalysisagent.agent.state;


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

    /**
     * 批量更新状态 - 使用Builder模式
     */
    public SubgraphState withUpdates(Map<String, Object> updates) {
        Map<String, Object> newData = new java.util.HashMap<>(this.data());
        newData.putAll(updates);
        return new SubgraphState(newData);
    }

    // === 兼容性方法（保持与原有AgentMessageState的兼容性） ===

    /**
     * 更新工具执行结果（兼容性方法）
     */
    public void setToolExecutionResult(String result) {
        // 使用Builder模式更新状态，然后通过反射替换
        SubgraphState updatedState = withToolExecutionResult(result);
        try {
            java.lang.reflect.Field dataField = findDataField();
            dataField.setAccessible(true);
            dataField.set(this, updatedState.data());
        } catch (Exception e) {
            throw new RuntimeException("Failed to update toolExecutionResult: " + e.getMessage(), e);
        }
    }

    /**
     * 更新最终响应（兼容性方法）
     */
    public void setFinalResponse(String finalResponse) {
        // 使用Builder模式更新状态，然后通过反射替换
        SubgraphState updatedState = withFinalResponse(finalResponse);
        try {
            java.lang.reflect.Field dataField = findDataField();
            dataField.setAccessible(true);
            dataField.set(this, updatedState.data());
        } catch (Exception e) {
            throw new RuntimeException("Failed to update finalResponse: " + e.getMessage(), e);
        }
    }

    /**
     * 更新执行状态（兼容性方法）
     */
    public void setExecutionStatus(String status) {
        // 使用Builder模式更新状态，然后通过反射替换
        SubgraphState updatedState = withExecutionStatus(status);
        try {
            java.lang.reflect.Field dataField = findDataField();
            dataField.setAccessible(true);
            dataField.set(this, updatedState.data());
        } catch (Exception e) {
            throw new RuntimeException("Failed to update executionStatus: " + e.getMessage(), e);
        }
    }

    /**
     * 查找data字段 - 辅助方法
     */
    private java.lang.reflect.Field findDataField() throws NoSuchFieldException {
        Class<?> currentClass = this.getClass();

        // 向上查找data字段
        while (currentClass != null) {
            try {
                return currentClass.getDeclaredField("data");
            } catch (NoSuchFieldException e) {
                // 尝试其他可能的字段名
                try {
                    return currentClass.getDeclaredField("state");
                } catch (NoSuchFieldException e2) {
                    try {
                        return currentClass.getDeclaredField("values");
                    } catch (NoSuchFieldException e3) {
                        // 继续向上查找
                    }
                }
            }
            currentClass = currentClass.getSuperclass();
        }

        throw new NoSuchFieldException("无法找到data字段，尝试了data、state、values等字段名");
    }
}
