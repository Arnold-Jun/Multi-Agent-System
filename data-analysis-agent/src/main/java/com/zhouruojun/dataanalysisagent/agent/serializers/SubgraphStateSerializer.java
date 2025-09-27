package com.zhouruojun.dataanalysisagent.agent.serializers;

import com.zhouruojun.dataanalysisagent.agent.state.SubgraphState;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.ChatMessage;
import org.bsc.langgraph4j.langchain4j.serializer.std.ToolExecutionRequestSerializer;
import org.bsc.langgraph4j.serializer.std.ObjectStreamStateSerializer;

/**
 * SubgraphState序列化器 - 使用ObjectStreamStateSerializer
 * 与agent-core保持一致，使用Java对象序列化而不是JSON序列化
 */
public class SubgraphStateSerializer extends ObjectStreamStateSerializer<SubgraphState> {
    
    public SubgraphStateSerializer() {
        super(SubgraphState::new);
        this.mapper().register(ToolExecutionRequest.class, new ToolExecutionRequestSerializer());
        this.mapper().register(ChatMessage.class, new ChatMessageSerializer());
    }
}


