package com.xiaohongshu.codewiz.codewizagent.cragent.agent.serializers;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.serializer.Serializer;

@Slf4j
public class CodeWizAiMessageSerializer implements Serializer<AiMessage> {
    public CodeWizAiMessageSerializer() {
    }

    public void write(AiMessage object, ObjectOutput out) throws IOException {
        boolean hasToolExecutionRequests = object.hasToolExecutionRequests();
        out.writeBoolean(hasToolExecutionRequests);
        if (hasToolExecutionRequests) {
            if (object.text() != null) {
                out.writeUTF(object.text());
            }
            out.writeObject(object.toolExecutionRequests());
//            out.writeObject(object);
        } else {
            out.writeUTF(object.text());
        }

    }

    public AiMessage read(ObjectInput in) throws IOException, ClassNotFoundException {
        boolean hasToolExecutionRequests = in.readBoolean();
        if (hasToolExecutionRequests) {
            String text = null;
            try {
                text = in.readUTF();
            } catch (Exception e) {
            }
            List<ToolExecutionRequest> toolExecutionRequests = (List)in.readObject();
            return AiMessage.aiMessage(text, toolExecutionRequests);
        } else {
            return AiMessage.aiMessage(in.readUTF());
        }
    }
}