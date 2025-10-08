package com.xiaohongshu.codewiz.codewizagent.agentcore.agent.state;

import com.xiaohongshu.codewiz.a2acore.spec.TaskSendParams;
import dev.langchain4j.data.message.ChatMessage;
import java.util.LinkedList;
import java.util.Map;
import java.util.Optional;
import org.apache.zookeeper.Op;
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

    public Optional<String> taskId() {
        return this.value("task_id");
    }

    public Optional<String> finalResponse() {
        return this.value("agent_response");
    }

    public Optional<String> toolInterceptor() {
        return this.value("tool_interceptor");
    }

    public Optional<String> sessionId() {
        return this.value("sessionId");
    }

    public Optional<String> requestId() {
        return this.value("sessionId");
    }

    public Optional<String> method() {
        return this.value("method");
    }

    public Optional<String> nextAgent() {
        return this.value("nextAgent");
    }

    public Optional<String> currentAgent() {
        return this.value("currentAgent");
    }

    public Optional<String> username() {
        return this.value("username");
    }

    public Optional<String> skill() {
        return this.value("skill");
    }
}
