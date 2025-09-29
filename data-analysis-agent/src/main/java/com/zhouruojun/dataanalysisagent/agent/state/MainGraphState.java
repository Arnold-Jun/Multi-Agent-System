package com.zhouruojun.dataanalysisagent.agent.state;

import com.zhouruojun.dataanalysisagent.agent.todo.TodoList;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Optional;

/**
 * 主图状态类
 * 包含主图执行所需的所有状态信息
 * 用于Planner、Scheduler、Summary等主图节点
 */
public class MainGraphState extends BaseAgentState {

    public MainGraphState(Map<String, Object> initData) {
        super(initData);
    }

    /**
     * 获取Todo列表
     */
    public Optional<TodoList> getTodoList() {
        return this.value("todoList").map(v -> (TodoList) v);
    }

    /**
     * 获取子图结果映射
     */
    @SuppressWarnings("unchecked")
    public Optional<Map<String, String>> getSubgraphResults() {
        return this.value("subgraphResults").map(v -> (Map<String, String>) v);
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

    // === 状态更新方法 ===

    /**
     * 更新TodoList状态 - 使用Builder模式
     */
    public MainGraphState withTodoList(TodoList todoList) {
        return withData("todoList", todoList, MainGraphState.class);
    }

    /**
     * 更新子图结果 - 使用Builder模式
     */
    public MainGraphState withSubgraphResult(String agentName, String result) {
        Map<String, String> results = getSubgraphResults().orElse(new java.util.HashMap<>());
        results.put(agentName, result);
        return withData("subgraphResults", results, MainGraphState.class);
    }

    /**
     * 批量更新子图结果 - 使用Builder模式
     */
    public MainGraphState withSubgraphResults(Map<String, String> newResults) {
        return withData("subgraphResults", new java.util.HashMap<>(newResults), MainGraphState.class);
    }



    /**
     * 更新TodoList状态（兼容性方法）
     */
    public void updateTodoList(TodoList todoList) {
        // 使用Builder模式更新状态，然后通过反射替换
        MainGraphState updatedState = withTodoList(todoList);
        try {
            Field dataField = findDataField();
            dataField.setAccessible(true);
            dataField.set(this, updatedState.data());
        } catch (Exception e) {
            throw new RuntimeException("Failed to update todoList: " + e.getMessage(), e);
        }
    }






    /**
     * 查找data字段 - 辅助方法
     */
    private Field findDataField() throws NoSuchFieldException {
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
