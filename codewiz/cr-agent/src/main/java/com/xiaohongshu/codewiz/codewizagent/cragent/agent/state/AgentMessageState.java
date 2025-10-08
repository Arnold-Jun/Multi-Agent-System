package com.xiaohongshu.codewiz.codewizagent.cragent.agent.state;

import dev.langchain4j.data.message.ChatMessage;
import java.util.Map;
import java.util.Optional;
import org.bsc.langgraph4j.prebuilt.MessagesState;

/**
 * <p>
 *
 * </p>
 *
 * @author 瑞诺
 * create on 2025/3/27 19:22
 */
public class AgentMessageState extends MessagesState<ChatMessage> {

    public AgentMessageState(Map<String, Object> initData) {
        super(initData);
    }

    public Optional<String> next() {
        return this.value("next");
    }

    public Optional<String> finalResponse() {
        return this.value("agent_response");
    }

    public Optional<String> toolInterceptor() {
        return this.value("tool_interceptor");
    }

    public Optional<String> getTaskId() {
        return this.value("requestId");
    }

    public Optional<String> getRequestId() {
        return this.value("requestId");
    }

    public Optional<String> getSessionId() {
        return this.value("sessionId");
    }

    public Optional<String> getUsername() {
        return this.value("username");
    }
}
