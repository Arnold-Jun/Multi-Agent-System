package com.zhouruojun.jobsearchagent.agent.todo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Todo任务类
 * 表示待办任务列表中的单个任务
 */
@Data
public class TodoTask implements Serializable {
    
    /**
     * 任务ID
     */
    @JsonProperty("taskId")
    private String taskId;
    
    /**
     * 任务描述
     */
    @JsonProperty("description")
    private String description;
    
    /**
     * 任务状态
     */
    @JsonProperty("status")
    private TaskStatus status;
    
    /**
     * 依赖的任务ID列表
     */
    @JsonProperty("dependencies")
    private List<String> dependencies;
    
    /**
     * 分配的子智能体名称
     */
    @JsonProperty("assignedAgent")
    private String assignedAgent;
    
    /**
     * 失败次数
     */
    @JsonProperty("failureCount")
    private int failureCount;
    
    /**
     * 任务创建时间戳
     */
    @JsonProperty("createdAt")
    private long createdAt;
    
    /**
     * 任务完成时间戳
     */
    @JsonProperty("completedAt")
    private Long completedAt;
    
    /**
     * 任务执行结果
     */
    @JsonProperty("result")
    private String result;
    
    /**
     * 任务在列表中的顺序
     */
    @JsonProperty("order")
    private Integer order;
    
    /**
     * 任务唯一ID
     */
    @JsonProperty("uniqueId")
    private String uniqueId;
    
    /**
     * 默认构造函数
     */
    public TodoTask() {
        this.status = TaskStatus.PENDING;
        this.failureCount = 0;
        this.createdAt = System.currentTimeMillis();
        this.dependencies = new ArrayList<>();
    }
    
    /**
     * 构造函数
     */
    public TodoTask(String taskId, String description, String assignedAgent) {
        this();
        this.taskId = taskId;
        this.uniqueId = taskId; // 设置uniqueId
        this.description = description;
        this.assignedAgent = assignedAgent;
    }
    
    /**
     * 构造函数（包含依赖）
     */
    public TodoTask(String taskId, String description, String assignedAgent, List<String> dependencies) {
        this(taskId, description, assignedAgent);
        this.dependencies = dependencies;
    }
    
    /**
     * 标记任务为进行中
     */
    public void markInProgress() {
        this.status = TaskStatus.IN_PROGRESS;
    }
    
    /**
     * 标记任务为完成
     */
    public void markCompleted() {
        this.status = TaskStatus.COMPLETED;
        this.completedAt = System.currentTimeMillis();
    }
    
    /**
     * 标记任务为失败
     */
    public void markFailed() {
        this.status = TaskStatus.FAILED;
        this.failureCount++;
    }
    
    /**
     * 检查任务是否可以执行（依赖是否满足）
     */
    public boolean canExecute(List<TodoTask> allTasks) {
        if (dependencies == null || dependencies.isEmpty()) {
            return true;
        }
        
        for (String depId : dependencies) {
            TodoTask depTask = allTasks.stream()
                    .filter(task -> task.getTaskId().equals(depId))
                    .findFirst()
                    .orElse(null);
            
            if (depTask == null || depTask.getStatus() != TaskStatus.COMPLETED) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * 检查任务是否连续失败3次及以上
     */
    public boolean isFailedTooManyTimes() {
        return failureCount >= 3;
    }
    
    /**
     * 复制构造函数
     */
    public TodoTask(TodoTask other) {
        this.taskId = other.taskId;
        this.description = other.description;
        this.assignedAgent = other.assignedAgent;
        this.status = other.status;
        this.failureCount = other.failureCount;
        this.createdAt = other.createdAt;
        this.completedAt = other.completedAt;
        this.result = other.result;
        this.dependencies = new ArrayList<>(other.dependencies);
        this.order = other.order;
        this.uniqueId = other.uniqueId;
    }
    
    /**
     * 获取任务摘要信息
     */
    public String getSummary() {
        return String.format("[%s] %s (%s) -> %s", 
                uniqueId != null ? uniqueId : taskId, description, status.getDescription(), assignedAgent);
    }
}
