package com.zhouruojun.jobsearchagent.agent.serializers;

import com.zhouruojun.jobsearchagent.agent.state.MainGraphState;
import com.zhouruojun.jobsearchagent.agent.todo.TodoList;
import com.zhouruojun.jobsearchagent.agent.todo.TodoTask;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.ChatMessage;
import org.bsc.langgraph4j.langchain4j.serializer.std.ToolExecutionRequestSerializer;
import org.bsc.langgraph4j.serializer.std.ObjectStreamStateSerializer;

/**
 * MainGraphState序列化器 - 使用ObjectStreamStateSerializer
 * 专门为主图状态设计，包含TodoList等主图特有的类型序列化
 */
public class MainGraphStateSerializer extends ObjectStreamStateSerializer<MainGraphState> {
    
    public MainGraphStateSerializer() {
        super(MainGraphState::new);
        
        // 注册LangChain4j相关类型的序列化器
        this.mapper().register(ToolExecutionRequest.class, new ToolExecutionRequestSerializer());
        this.mapper().register(ChatMessage.class, new ChatMessageSerializer());
        
        // 注册主图特有的类型序列化器
        this.mapper().register(TodoList.class, new TodoListSerializer());
        this.mapper().register(TodoTask.class, new TodoTaskSerializer());
    }
}
