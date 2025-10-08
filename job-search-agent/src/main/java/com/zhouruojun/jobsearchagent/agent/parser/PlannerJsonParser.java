package com.zhouruojun.jobsearchagent.agent.parser;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.zhouruojun.jobsearchagent.agent.todo.TodoList;
import com.zhouruojun.jobsearchagent.agent.todo.TodoTask;
import com.zhouruojun.jobsearchagent.agent.todo.TaskStatus;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Planner JSON解析器
 * 专门用于解析Planner输出的JSON格式，并更新TodoList
 */
@Slf4j
public class PlannerJsonParser {

    /**
     * 解析并更新TodoList
     * 
     * @param plannerOutput Planner的JSON输出
     * @param existingTodoList 现有的TodoList（可能为null）
     * @return 更新后的TodoList
     * @throws PlannerParseException 解析异常
     */
    public TodoList parseAndUpdateTodoList(String plannerOutput, TodoList existingTodoList) throws PlannerParseException {
        try {
            log.info("开始解析Planner JSON输出");
            
            // 解析JSON
            JSONObject jsonObject = JSON.parseObject(plannerOutput);
            
            // 创建新的TodoList或使用现有的
            TodoList todoList = existingTodoList != null ? existingTodoList : new TodoList();
            
            // 处理删除操作
            processDeleteOperations(jsonObject, todoList);
            
            // 处理修改操作
            processModifyOperations(jsonObject, todoList);
            
            // 处理添加操作
            processAddOperations(jsonObject, todoList);
            
            log.info("TodoList解析完成，当前任务数量: {}", todoList.getTasks().size());
            return todoList;
            
        } catch (Exception e) {
            log.error("解析Planner JSON输出失败: {}", e.getMessage(), e);
            throw new PlannerParseException("解析Planner JSON输出失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 处理删除操作
     */
    private void processDeleteOperations(JSONObject jsonObject, TodoList todoList) {
        JSONArray deleteArray = jsonObject.getJSONArray("delete");
        if (deleteArray != null && !deleteArray.isEmpty()) {
            log.info("处理删除操作，数量: {}", deleteArray.size());
            
            for (int i = 0; i < deleteArray.size(); i++) {
                JSONObject deleteItem = deleteArray.getJSONObject(i);
                String uniqueId = deleteItem.getString("uniqueId");
                
                if (uniqueId != null && !uniqueId.trim().isEmpty()) {
                    boolean removed = todoList.removeTask(uniqueId);
                    if (removed) {
                        log.info("成功删除任务: {}", uniqueId);
                    } else {
                        log.warn("删除任务失败，任务不存在: {}", uniqueId);
                    }
                } else {
                    log.warn("删除操作缺少uniqueId字段");
                }
            }
        }
    }
    
    /**
     * 处理修改操作
     */
    private void processModifyOperations(JSONObject jsonObject, TodoList todoList) {
        JSONArray modifyArray = jsonObject.getJSONArray("modify");
        if (modifyArray != null && !modifyArray.isEmpty()) {
            log.info("处理修改操作，数量: {}", modifyArray.size());
            
            for (int i = 0; i < modifyArray.size(); i++) {
                JSONObject modifyItem = modifyArray.getJSONObject(i);
                String uniqueId = modifyItem.getString("uniqueId");
                
                if (uniqueId != null && !uniqueId.trim().isEmpty()) {
                    // 查找现有任务
                    TodoTask existingTask = findTaskById(todoList, uniqueId);
                    if (existingTask != null) {
                        // 更新任务属性
                        updateTaskFromJson(existingTask, modifyItem);
                        log.info("成功修改任务: {}", uniqueId);
                    } else {
                        log.warn("修改任务失败，任务不存在: {}", uniqueId);
                    }
                } else {
                    log.warn("修改操作缺少uniqueId字段");
                }
            }
        }
    }
    
    /**
     * 处理添加操作
     */
    private void processAddOperations(JSONObject jsonObject, TodoList todoList) {
        JSONArray addArray = jsonObject.getJSONArray("add");
        if (addArray != null && !addArray.isEmpty()) {
            log.info("处理添加操作，数量: {}", addArray.size());
            
            for (int i = 0; i < addArray.size(); i++) {
                JSONObject addItem = addArray.getJSONObject(i);
                TodoTask newTask = createTaskFromJson(addItem);
                
                if (newTask != null) {
                    todoList.addTask(newTask);
                    log.info("成功添加任务: {}", newTask.getTaskId());
                } else {
                    log.warn("添加任务失败，任务信息不完整");
                }
            }
        }
    }
    
    /**
     * 根据ID查找任务
     */
    private TodoTask findTaskById(TodoList todoList, String id) {
        return todoList.getTasks().stream()
                .filter(task -> id.equals(task.getTaskId()) || id.equals(task.getUniqueId()))
                .findFirst()
                .orElse(null);
    }
    
    /**
     * 从JSON创建任务
     */
    private TodoTask createTaskFromJson(JSONObject taskJson) {
        try {
            String description = taskJson.getString("description");
            String assignedAgent = taskJson.getString("assignedAgent");
            String status = taskJson.getString("status");
            Integer order = taskJson.getInteger("order");
            
            if (description == null || description.trim().isEmpty()) {
                log.warn("任务描述不能为空");
                return null;
            }
            
            if (assignedAgent == null || assignedAgent.trim().isEmpty()) {
                log.warn("分配智能体不能为空");
                return null;
            }
            
            // 生成任务ID
            String taskId = "task_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);
            
            // 创建任务
            TodoTask task = new TodoTask(taskId, description, assignedAgent);
            task.setUniqueId(taskId);
            
            // 设置状态
            if (status != null && !status.trim().isEmpty()) {
                task.setStatus(parseTaskStatus(status));
            } else {
                task.setStatus(TaskStatus.PENDING);
            }
            
            // 设置顺序
            if (order != null) {
                task.setOrder(order);
            } else {
                task.setOrder(0);
            }
            
            return task;
            
        } catch (Exception e) {
            log.error("从JSON创建任务失败: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * 从JSON更新任务
     */
    private void updateTaskFromJson(TodoTask task, JSONObject updateJson) {
        try {
            // 更新描述
            String description = updateJson.getString("description");
            if (description != null && !description.trim().isEmpty()) {
                task.setDescription(description);
            }
            
            // 更新分配智能体
            String assignedAgent = updateJson.getString("assignedAgent");
            if (assignedAgent != null && !assignedAgent.trim().isEmpty()) {
                task.setAssignedAgent(assignedAgent);
            }
            
            // 更新状态
            String status = updateJson.getString("status");
            if (status != null && !status.trim().isEmpty()) {
                task.setStatus(parseTaskStatus(status));
            }
            
            // 更新顺序
            Integer order = updateJson.getInteger("order");
            if (order != null) {
                task.setOrder(order);
            }
            
            // 更新结果
            String result = updateJson.getString("result");
            if (result != null) {
                task.setResult(result);
            }
            
            // 更新错误信息（暂时注释掉，因为TodoTask没有setError方法）
            // String errorMessage = updateJson.getString("errorMessage");
            // if (errorMessage != null) {
            //     // TODO: 如果需要错误信息功能，需要在TodoTask中添加相应字段和方法
            // }
            
        } catch (Exception e) {
            log.error("从JSON更新任务失败: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 解析任务状态字符串为TaskStatus枚举
     */
    private TaskStatus parseTaskStatus(String statusStr) {
        switch (statusStr.toLowerCase()) {
            case "pending":
            case "待执行":
                return TaskStatus.PENDING;
            case "in_progress":
            case "执行中":
                return TaskStatus.IN_PROGRESS;
            case "completed":
            case "finished":
            case "已完成":
                return TaskStatus.COMPLETED;
            case "failed":
            case "失败":
                return TaskStatus.FAILED;
            default:
                log.warn("未知的任务状态: {}, 使用默认状态PENDING", statusStr);
                return TaskStatus.PENDING;
        }
    }
    
    /**
     * 解析异常类
     */
    public static class PlannerParseException extends Exception {
        public PlannerParseException(String message) {
            super(message);
        }
        
        public PlannerParseException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
