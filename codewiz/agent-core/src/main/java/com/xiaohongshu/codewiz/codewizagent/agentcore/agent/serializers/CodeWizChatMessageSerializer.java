package com.xiaohongshu.codewiz.codewizagent.agentcore.agent.serializers;

import com.xiaohongshu.codewiz.codewizagent.agentcore.agent.serializers.CodeWizAiMessageSerializer;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import org.bsc.langgraph4j.langchain4j.serializer.std.SystemMessageSerializer;
import org.bsc.langgraph4j.langchain4j.serializer.std.ToolExecutionResultMessageSerializer;
import org.bsc.langgraph4j.langchain4j.serializer.std.UserMessageSerializer;
import org.bsc.langgraph4j.serializer.Serializer;

public class CodeWizChatMessageSerializer implements Serializer<ChatMessage> {
    final CodeWizAiMessageSerializer ai = new CodeWizAiMessageSerializer();
    final UserMessageSerializer user = new UserMessageSerializer();
    final SystemMessageSerializer system = new SystemMessageSerializer();
    final ToolExecutionResultMessageSerializer toolExecutionResult = new ToolExecutionResultMessageSerializer();

    public CodeWizChatMessageSerializer() {
    }

    public void write(ChatMessage object, ObjectOutput out) throws IOException {
        out.writeObject(object.type());
        switch (object.type()) {
            case AI -> this.ai.write((AiMessage)object, out);
            case USER -> this.user.write((UserMessage)object, out);
            case SYSTEM -> this.system.write((SystemMessage)object, out);
            case TOOL_EXECUTION_RESULT -> this.toolExecutionResult.write((ToolExecutionResultMessage)object, out);
        }

    }

    public ChatMessage read(ObjectInput in) throws IOException, ClassNotFoundException {
        ChatMessageType type = (ChatMessageType)in.readObject();
        switch (type) {
            case AI -> {
                return this.ai.read(in);
            }
            case USER -> {
                return this.user.read(in);
            }
            case SYSTEM -> {
                return this.system.read(in);
            }
            case TOOL_EXECUTION_RESULT -> {
                return this.toolExecutionResult.read(in);
            }
            default -> throw new IllegalArgumentException("Unsupported chat message type: " + String.valueOf(type));
        }
    }
}
