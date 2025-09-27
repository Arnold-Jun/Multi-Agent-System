package com.zhouruojun.dataanalysisagent.agent.serializers;

import com.zhouruojun.dataanalysisagent.agent.todo.TodoList;
import org.bsc.langgraph4j.serializer.Serializer;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 * TodoList序列化器
 */
public class TodoListSerializer implements Serializer<TodoList> {
    
    @Override
    public void write(TodoList object, ObjectOutput out) throws IOException {
        if (object == null) {
            out.writeObject(null);
            return;
        }
        
        out.writeObject(object.getTasks());
        out.writeObject(object.getOriginalQuery());
        out.writeLong(object.getCreatedAt());
        out.writeLong(object.getUpdatedAt());
    }
    
    @Override
    public TodoList read(ObjectInput in) throws IOException, ClassNotFoundException {
        Object tasksObj = in.readObject();
        if (tasksObj == null) {
            return null;
        }
        
        String originalQuery = (String) in.readObject();
        long createdAt = in.readLong();
        long updatedAt = in.readLong();
        
        TodoList todoList = new TodoList(originalQuery);
        @SuppressWarnings("unchecked")
        java.util.List<com.zhouruojun.dataanalysisagent.agent.todo.TodoTask> tasks = 
            (java.util.List<com.zhouruojun.dataanalysisagent.agent.todo.TodoTask>) tasksObj;
        todoList.setTasks(tasks);
        todoList.setCreatedAt(createdAt);
        todoList.setUpdatedAt(updatedAt);
        
        return todoList;
    }
}
