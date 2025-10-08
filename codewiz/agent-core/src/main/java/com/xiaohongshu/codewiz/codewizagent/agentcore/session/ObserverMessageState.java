package com.xiaohongshu.codewiz.codewizagent.agentcore.session;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageSerializer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;

/**
 * <p>
 *
 * </p>
 *
 * @author 瑞诺
 * create on 2025/4/9 21:07
 */
@Data
public class ObserverMessageState {

    private Map<String, Object> data = new HashMap<>();

    public void of(Map<String, Object> stateData) {
        stateData.forEach( (k,v)->{
            if (v instanceof List) {
                List<ChatMessage> value = (List<ChatMessage>)v;
                List<ChatMessage> chatMessages;
                if (value.size() < 4) {
                    chatMessages = value;
                } else {
                    chatMessages = value.subList(value.size() - 3, value.size());
                }
                String s = ChatMessageSerializer.messagesToJson(chatMessages);
                data.put(k, s);
            } else {
                data.put(k, v.toString());
            }
        });
    }

}
