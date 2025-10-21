package com.zhouruojun.travelingagent.agent.state.main;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Todo列表管理类
 * 管理整个任务列表的状态和操作
 */
@Data
public class TodoList implements Serializable {
    
    /**
     * 任务列表
     */
    @JsonProperty("tasks")
    private List<TodoTask> tasks;
    
    /**
     * 原始用户查询
     */
    @JsonProperty("originalQuery")
    private String originalQuery;
    
    /**
     * 创建时间戳
     */
    @JsonProperty("createdAt")
    private long createdAt;
    
    /**
     * 最后更新时间戳
     */
    @JsonProperty("updatedAt")
    private long updatedAt;
    
    /**
     * 默认构造函数
     */
    public TodoList() {
        this.tasks = new ArrayList<>();
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
    }
    
    /**
     * 构造函数
     */
    public TodoList(String originalQuery) {
        this();
        this.originalQuery = originalQuery;
    }
    
    /**
     * 添加任务
     */
    public void addTask(TodoTask task) {
        tasks.add(task);
        updateTimestamp();
    }
    
    /**
     * 根据ID获取任务
     */
    public TodoTask getTaskById(String taskId) {
        return tasks.stream()
                .filter(task -> task.getTaskId().equals(taskId))
                .findFirst()
                .orElse(null);
    }
    
    /**
     * 更新任务状态
     */
    public boolean updateTaskStatus(String taskId, TaskStatus status) {
        TodoTask task = getTaskById(taskId);
        if (task != null) {
            task.setStatus(status);
            updateTimestamp();
            return true;
        }
        return false;
    }
    
    /**
     * 标记任务完成
     */
    public boolean markTaskCompleted(String taskId) {
        TodoTask task = getTaskById(taskId);
        if (task != null) {
            task.markCompleted();
            updateTimestamp();
            return true;
        }
        return false;
    }
    
    /**
     * 标记任务失败
     */
    public boolean markTaskFailed(String taskId) {
        TodoTask task = getTaskById(taskId);
        if (task != null) {
            task.markFailed();
            updateTimestamp();
            return true;
        }
        return false;
    }
    
    /**
     * 删除任务
     */
    public boolean removeTask(String taskId) {
        boolean removed = tasks.removeIf(task -> task.getTaskId().equals(taskId));
        if (removed) {
            updateTimestamp();
        }
        return removed;
    }
    
    /**
     * 检查是否有可执行的任务
     */
    public boolean hasExecutableTasks() {
        return getNextExecutableTask() != null;
    }
    
    /**
     * 获取下一个可执行的任务
     */
    public TodoTask getNextExecutableTask() {
        return tasks.stream()
                .filter(task -> task.getStatus() == TaskStatus.PENDING)
                .filter(task -> task.canExecute(tasks))
                .findFirst()
                .orElse(null);
    }
    
    /**
     * 获取所有待执行的任务
     */
    public List<TodoTask> getPendingTasks() {
        return tasks.stream()
                .filter(task -> task.getStatus() == TaskStatus.PENDING)
                .collect(Collectors.toList());
    }
    
    /**
     * 获取所有进行中的任务
     */
    public List<TodoTask> getInProgressTasks() {
        return tasks.stream()
                .filter(task -> task.getStatus() == TaskStatus.IN_PROGRESS)
                .collect(Collectors.toList());
    }
    
    /**
     * 获取所有已完成的任务
     */
    public List<TodoTask> getCompletedTasks() {
        return tasks.stream()
                .filter(task -> task.getStatus() == TaskStatus.COMPLETED)
                .collect(Collectors.toList());
    }
    
    /**
     * 获取所有失败的任务
     */
    public List<TodoTask> getFailedTasks() {
        return tasks.stream()
                .filter(task -> task.getStatus() == TaskStatus.FAILED)
                .collect(Collectors.toList());
    }
    
    /**
     * 检查是否所有任务都已完成
     */
    public boolean isAllCompleted() {
        return tasks.stream()
                .allMatch(task -> task.getStatus() == TaskStatus.COMPLETED);
    }
    
    /**
     * 检查是否有任务失败过多需要终止
     */
    public boolean hasFailedTooManyTimes() {
        return tasks.stream()
                .anyMatch(TodoTask::isFailedTooManyTimes);
    }
    
    /**
     * 检查是否可以终止（所有完成或有失败过多）
     */
    public boolean canTerminate() {
        return isAllCompleted() || hasFailedTooManyTimes();
    }
    
    /**
     * 获取任务统计信息
     */
    public String getStatistics() {
        long total = tasks.size();
        long pending = getPendingTasks().size();
        long inProgress = getInProgressTasks().size();
        long completed = getCompletedTasks().size();
        long failed = getFailedTasks().size();
        
        // 统计有失败记录的任务（包括重试中的任务）
        long tasksWithFailures = tasks.stream()
                .filter(task -> task.getFailureCount() > 0)
                .count();
        
        if (tasksWithFailures > 0) {
            return String.format("总任务: %d, 待执行: %d, 执行中: %d, 已完成: %d, 失败: %d (其中%d个任务有失败记录)", 
                    total, pending, inProgress, completed, failed, tasksWithFailures);
        } else {
            return String.format("总任务: %d, 待执行: %d, 执行中: %d, 已完成: %d, 失败: %d", 
                    total, pending, inProgress, completed, failed);
        }
    }
    
    /**
     * 根据唯一ID查找任务
     */
    public TodoTask findTaskByUniqueId(String uniqueId) {
        return tasks.stream()
                .filter(task -> uniqueId.equals(task.getUniqueId()))
                .findFirst()
                .orElse(null);
    }
    
    /**
     * 根据唯一ID删除任务
     */
    public boolean removeTaskByUniqueId(String uniqueId) {
        return tasks.removeIf(task -> uniqueId.equals(task.getUniqueId()));
    }
    
    /**
     * 复制构造函数
     */
    public TodoList(TodoList other) {
        this.originalQuery = other.originalQuery;
        this.createdAt = other.createdAt;
        this.tasks = new ArrayList<>();
        for (TodoTask task : other.tasks) {
            this.tasks.add(new TodoTask(task));
        }
    }
    
    /**
     * 获取任务列表摘要
     */
    public String getSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("任务列表摘要:\n");
        summary.append(getStatistics()).append("\n\n");
        
        for (TodoTask task : tasks) {
            summary.append(task.getSummary()).append("\n");
        }
        
        return summary.toString();
    }
    
    /**
     * 更新时间戳
     */
    private void updateTimestamp() {
        this.updatedAt = System.currentTimeMillis();
    }
}


