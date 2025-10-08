package com.zhouruojun.jobsearchagent.agent.serializers;

import com.zhouruojun.jobsearchagent.agent.todo.TodoTask;
import org.bsc.langgraph4j.serializer.Serializer;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 * TodoTask序列化器
 */
public class TodoTaskSerializer implements Serializer<TodoTask> {
    
    @Override
    public void write(TodoTask object, ObjectOutput out) throws IOException {
        if (object == null) {
            out.writeObject(null);
            return;
        }
        
        // 确保所有字段都不为null，使用默认值替代
        out.writeObject(object.getTaskId() != null ? object.getTaskId() : "");
        out.writeObject(object.getDescription() != null ? object.getDescription() : "");
        out.writeObject(object.getStatus() != null ? object.getStatus() : com.zhouruojun.jobsearchagent.agent.todo.TaskStatus.PENDING);
        out.writeObject(object.getDependencies() != null ? object.getDependencies() : new java.util.ArrayList<>());
        out.writeObject(object.getAssignedAgent() != null ? object.getAssignedAgent() : "");
        out.writeInt(object.getFailureCount());
        out.writeLong(object.getCreatedAt());
        
        // completedAt可能为null，使用特殊标记处理
        Long completedAt = object.getCompletedAt();
        out.writeLong(completedAt != null ? completedAt : -1L);
        
        out.writeObject(object.getResult() != null ? object.getResult() : "");
        out.writeObject(object.getOrder());
        out.writeObject(object.getUniqueId() != null ? object.getUniqueId() : "");
    }
    
    @Override
    public TodoTask read(ObjectInput in) throws IOException, ClassNotFoundException {
        String taskId = (String) in.readObject();
        if (taskId == null) {
            return null;
        }
        
        String description = (String) in.readObject();
        Object statusObj = in.readObject();
        Object dependenciesObj = in.readObject();
        String assignedAgent = (String) in.readObject();
        int failureCount = in.readInt();
        long createdAt = in.readLong();
        
        // completedAt：使用-1L标记null
        long completedAtLong = in.readLong();
        
        String result = (String) in.readObject();
        Integer order = (Integer) in.readObject();
        String uniqueId = (String) in.readObject();
        
        TodoTask task = new TodoTask(taskId, description, assignedAgent);
        if (statusObj != null) {
            task.setStatus((com.zhouruojun.jobsearchagent.agent.todo.TaskStatus) statusObj);
        }
        if (dependenciesObj != null) {
            @SuppressWarnings("unchecked")
            java.util.List<String> dependencies = (java.util.List<String>) dependenciesObj;
            task.setDependencies(dependencies);
        }
        task.setFailureCount(failureCount);
        task.setCreatedAt(createdAt);
        if (completedAtLong >= 0L) {  // -1L表示null
            task.setCompletedAt(completedAtLong);
        }
        task.setResult(result);
        task.setOrder(order);
        task.setUniqueId(uniqueId);
        
        return task;
    }
}
