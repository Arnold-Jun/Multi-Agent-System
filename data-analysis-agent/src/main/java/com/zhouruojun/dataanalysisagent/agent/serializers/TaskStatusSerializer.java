package com.zhouruojun.dataanalysisagent.agent.serializers;

import com.zhouruojun.dataanalysisagent.agent.todo.TaskStatus;
import org.bsc.langgraph4j.serializer.Serializer;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 * TaskStatus序列化器
 */
public class TaskStatusSerializer implements Serializer<TaskStatus> {
    
    @Override
    public void write(TaskStatus object, ObjectOutput out) throws IOException {
        if (object == null) {
            out.writeObject(null);
        } else {
            out.writeObject(object.getCode());
        }
    }
    
    @Override
    public TaskStatus read(ObjectInput in) throws IOException, ClassNotFoundException {
        String code = (String) in.readObject();
        if (code == null) {
            return null;
        }
        try {
            return TaskStatus.fromCode(code);
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid TaskStatus code: " + code, e);
        }
    }
}
