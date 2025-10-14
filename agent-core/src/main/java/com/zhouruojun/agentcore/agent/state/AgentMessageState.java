package com.zhouruojun.agentcore.agent.state;

import dev.langchain4j.data.message.ChatMessage;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import java.util.Map;
import java.util.Optional;

/**
 * Agent消息状态管理类
 * 扩展MessagesState以支持智能体间的复杂状态管理
 * 
 */
public class AgentMessageState extends MessagesState<ChatMessage> {

    public AgentMessageState(Map<String, Object> initData) {
        super(initData);
    }

    /**
     * 获取下一个执行节点
     */
    public Optional<String> next() {
        return this.value("next");
    }

    /**
     * 获取任务ID
     */
    public Optional<String> taskId() {
        return this.value("task_id");
    }

    /**
     * 获取最终响应
     */
    public Optional<String> finalResponse() {
        return this.value("agent_response");
    }

    /**
     * 获取工具拦截器
     */
    public Optional<String> toolInterceptor() {
        return this.value("tool_interceptor");
    }

    /**
     * 获取会话ID
     */
    public Optional<String> sessionId() {
        return this.value("sessionId");
    }

    /**
     * 获取请求ID
     */
    public Optional<String> requestId() {
        return this.value("requestId");
    }

    /**
     * 获取执行方法
     */
    public Optional<String> method() {
        return this.value("method");
    }

    /**
     * 获取下一个智能体
     */
    public Optional<String> nextAgent() {
        return this.value("nextAgent");
    }

    /**
     * 获取当前智能体
     */
    public Optional<String> currentAgent() {
        return this.value("currentAgent");
    }

    /**
     * 获取用户名
     */
    public Optional<String> username() {
        return this.value("username");
    }

    /**
     * 获取技能信息
     */
    public Optional<String> skill() {
        return this.value("skill");
    }

    /**
     * 获取智能体响应
     */
    public Optional<String> agentResponse() {
        return this.value("agent_response");
    }

    /**
     * 获取任务指令
     */
    public Optional<String> taskInstruction() {
        return this.value("taskInstruction");
    }


    /**
     * 获取用户邮箱
     */
    public Optional<String> userEmail() {
        return this.value("userEmail");
    }

    /**
     * 获取工具规范JSON
     */
    public Optional<String> userTools() {
        return this.value("userTools");
    }

    /**
     * 获取技能列表
     */
    public Optional<String> skills() {
        return this.value("skills");
    }

    /**
     * 检查是否有消息历史
     */
    public boolean hasMessages() {
        return !messages().isEmpty();
    }

    /**
     * 获取最后一条消息
     */
    public Optional<ChatMessage> lastMessage() {
        var messages = messages();
        if (messages.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(messages.get(messages.size() - 1));
    }
}
