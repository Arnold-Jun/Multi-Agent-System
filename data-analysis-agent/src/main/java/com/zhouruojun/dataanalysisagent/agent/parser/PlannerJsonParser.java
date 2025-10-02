package com.zhouruojun.dataanalysisagent.agent.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhouruojun.dataanalysisagent.agent.todo.TodoList;
import com.zhouruojun.dataanalysisagent.agent.todo.TodoTask;
import com.zhouruojun.dataanalysisagent.agent.todo.TaskStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Planner输出JSON解析器
 * 负责解析Planner输出的JSON格式，并更新TodoList
 */
@Slf4j
@Component
public class PlannerJsonParser {
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * 解析Planner输出的JSON并更新TodoList
     * 
     * @param jsonOutput Planner输出的JSON字符串
     * @param currentTodoList 当前的TodoList
     * @return 更新后的TodoList
     * @throws PlannerParseException 解析失败时抛出异常
     */
    public TodoList parseAndUpdateTodoList(String jsonOutput, TodoList currentTodoList) throws PlannerParseException {
        try {
            JsonNode rootNode = objectMapper.readTree(jsonOutput);
            
            // 创建新的TodoList副本
            TodoList updatedTodoList = currentTodoList != null ? 
                new TodoList(currentTodoList) : new TodoList("用户查询");
            
            // 检测矛盾操作：同时出现的delete和modify
            Set<String> deleteIds = new HashSet<>();
            Set<String> modifyIds = new HashSet<>();
            
            if (rootNode.has("delete")) {
                deleteIds = extractUniqueIds(rootNode.get("delete"));
            }
            if (rootNode.has("modify")) {
                modifyIds = extractUniqueIds(rootNode.get("modify"));
            }
            
            // 找出矛盾的任务（既被删除又被修改）
            Set<String> conflictIds = new HashSet<>(deleteIds);
            conflictIds.retainAll(modifyIds);
            
            if (!conflictIds.isEmpty()) {
                log.warn("检测到矛盾操作：任务{}既被删除又被修改，优先执行修改操作: {}", 
                    conflictIds, String.join(", ", conflictIds));
            }
            
            // 1. 执行delete操作（跳过矛盾的任务）
            if (rootNode.has("delete")) {
                executeDeleteOperationsWithConflictDetection(rootNode.get("delete"), updatedTodoList, conflictIds);
            }
            
            // 2. 执行modify操作
            if (rootNode.has("modify")) {
                executeModifyOperations(rootNode.get("modify"), updatedTodoList);
            }
            
            // 3. 最后执行add操作
            if (rootNode.has("add")) {
                executeAddOperations(rootNode.get("add"), updatedTodoList);
            }
            
            log.info("TodoList解析和更新完成，当前任务数量: {}", updatedTodoList.getTasks().size());
            return updatedTodoList;
            
        } catch (Exception e) {
            log.error("解析Planner JSON输出失败: {}", e.getMessage(), e);
            throw new PlannerParseException("JSON解析失败: " + e.getMessage(), e);
        }
    }
    
    
    /**
     * 执行修改操作
     */
    private void executeModifyOperations(JsonNode modifyNode, TodoList todoList) throws PlannerParseException {
        if (modifyNode.isArray()) {
            for (JsonNode modifyItem : modifyNode) {
                if (modifyItem.has("uniqueId")) {
                    String uniqueId = modifyItem.get("uniqueId").asText();
                    TodoTask task = todoList.findTaskByUniqueId(uniqueId);
                    
                    if (task != null) {
                        // 修改任务状态
                        if (modifyItem.has("status")) {
                            String statusStr = modifyItem.get("status").asText();
                            TaskStatus newStatus = parseTaskStatus(statusStr);
                            task.setStatus(newStatus);
                            
                            if (newStatus == TaskStatus.COMPLETED) {
                                task.setCompletedAt(System.currentTimeMillis());
                            }
                            log.info("修改任务 {} 状态为: {}", uniqueId, newStatus);
                        }
                        
                        // 修改任务描述
                        if (modifyItem.has("description")) {
                            task.setDescription(modifyItem.get("description").asText());
                            log.info("修改任务 {} 描述", uniqueId);
                        }
                        
                        // 修改分配智能体
                        if (modifyItem.has("assignedAgent")) {
                            task.setAssignedAgent(modifyItem.get("assignedAgent").asText());
                            log.info("修改任务 {} 分配智能体", uniqueId);
                        }
                    } else {
                        // 收集所有可用的任务ID用于错误信息
                        List<String> availableIds = todoList.getTasks().stream()
                            .map(t -> t.getUniqueId() != null ? t.getUniqueId() : t.getTaskId())
                            .toList();
                        
                        String errorMsg = String.format("未找到要修改的任务: %s。可用的任务ID: %s", uniqueId, availableIds);
                        log.warn(errorMsg);
                        throw new PlannerParseException(errorMsg, null);
                    }
                }
            }
        }
    }
    
    /**
     * 执行添加操作
     */
    private void executeAddOperations(JsonNode addNode, TodoList todoList) {
        if (addNode.isArray()) {
            for (JsonNode addItem : addNode) {
                try {
                    String description = addItem.get("description").asText();
                    String assignedAgent = addItem.get("assignedAgent").asText();
                    int order = addItem.has("order") ? addItem.get("order").asInt() : todoList.getTasks().size() + 1;
                    
                    // 生成唯一ID
                    String uniqueId = generateUniqueId(order);
                    
                    TodoTask newTask = new TodoTask(uniqueId, description, assignedAgent);
                    newTask.setOrder(order);
                    
                    // 设置任务状态
                    if (addItem.has("status")) {
                        String statusStr = addItem.get("status").asText();
                        newTask.setStatus(parseTaskStatus(statusStr));
                    }
                    
                    todoList.addTask(newTask);
                    log.info("添加新任务: {} - {} -> {} (order: {})", 
                            uniqueId, description, assignedAgent, order);
                    
                } catch (Exception e) {
                    log.error("添加任务失败: {}", e.getMessage(), e);
                }
            }
        }
    }
    
    /**
     * 解析任务状态
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
     * 生成任务唯一ID
     */
    private String generateUniqueId(int order) {
        long timestamp = System.currentTimeMillis();
        int random = new Random().nextInt(1000);
        return String.format("task_%d_%d_%03d", order, timestamp, random);
    }
    
    /**
     * 提取JSON节点中的uniqueId集合
     */
    private Set<String> extractUniqueIds(JsonNode jsonArray) {
        Set<String> ids = new HashSet<>();
        if (jsonArray.isArray()) {
            for (JsonNode item : jsonArray) {
                if (item.has("uniqueId")) {
                    ids.add(item.get("uniqueId").asText());
                }
            }
        }
        return ids;
    }
    
    /**
     * 执行删除操作（跳过矛盾任务）
     */
    private void executeDeleteOperationsWithConflictDetection(JsonNode deleteNode, TodoList todoList, Set<String> conflictIds) {
        if (deleteNode.isArray()) {
            for (JsonNode deleteItem : deleteNode) {
                if (deleteItem.has("uniqueId")) {
                    String uniqueId = deleteItem.get("uniqueId").asText();
                    
                    // 跳过矛盾的任务（既被删除又被修改）
                    if (conflictIds.contains(uniqueId)) {
                        log.info("跳过删除任务 {}（由于矛盾操作），优先执行修改操作", uniqueId);
                        continue;
                    }
                    
                    todoList.removeTaskByUniqueId(uniqueId);
                    log.info("删除任务: {}", uniqueId);
                }
            }
        }
    }

    /**
     * Planner解析异常
     */
    public static class PlannerParseException extends Exception {
        public PlannerParseException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

