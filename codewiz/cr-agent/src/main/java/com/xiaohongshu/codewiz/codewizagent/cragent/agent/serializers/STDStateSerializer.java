package com.xiaohongshu.codewiz.codewizagent.cragent.agent.serializers;

import com.xiaohongshu.codewiz.codewizagent.cragent.agent.state.AgentMessageState;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.ChatMessage;
import org.bsc.langgraph4j.langchain4j.serializer.std.ToolExecutionRequestSerializer;
import org.bsc.langgraph4j.serializer.std.ObjectStreamStateSerializer;

public class STDStateSerializer extends ObjectStreamStateSerializer<AgentMessageState> {
    public STDStateSerializer() {
        super(AgentMessageState::new);
        this.mapper().register(ToolExecutionRequest.class, new ToolExecutionRequestSerializer());
        this.mapper().register(ChatMessage.class, new CodeWizChatMessageSerializer());
    }
}