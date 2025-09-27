package com.zhouruojun.dataanalysisagent.agent.serializers;

import com.zhouruojun.dataanalysisagent.agent.todo.TodoTask;
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
        
        out.writeObject(object.getTaskId());
        out.writeObject(object.getDescription());
        out.writeObject(object.getStatus());
        out.writeObject(object.getDependencies());
        out.writeObject(object.getAssignedAgent());
        out.writeInt(object.getFailureCount());
        out.writeLong(object.getCreatedAt());
        out.writeObject(object.getCompletedAt());
        out.writeObject(object.getResult());
        out.writeObject(object.getOrder());
        out.writeObject(object.getUniqueId());
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
        Object completedAtObj = in.readObject();
        String result = (String) in.readObject();
        Integer order = (Integer) in.readObject();
        String uniqueId = (String) in.readObject();
        
        TodoTask task = new TodoTask(taskId, description, assignedAgent);
        if (statusObj != null) {
            task.setStatus((com.zhouruojun.dataanalysisagent.agent.todo.TaskStatus) statusObj);
        }
        if (dependenciesObj != null) {
            @SuppressWarnings("unchecked")
            java.util.List<String> dependencies = (java.util.List<String>) dependenciesObj;
            task.setDependencies(dependencies);
        }
        task.setFailureCount(failureCount);
        task.setCreatedAt(createdAt);
        if (completedAtObj != null) {
            task.setCompletedAt((Long) completedAtObj);
        }
        task.setResult(result);
        task.setOrder(order);
        task.setUniqueId(uniqueId);
        
        return task;
    }
}
