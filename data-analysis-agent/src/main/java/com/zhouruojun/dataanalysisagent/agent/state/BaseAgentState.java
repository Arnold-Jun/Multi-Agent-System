package com.zhouruojun.dataanalysisagent.agent.state;

import dev.langchain4j.data.message.ChatMessage;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import java.util.Map;
import java.util.Optional;

/**
 * 基础智能体状态类
 * 包含所有智能体状态共有的字段和方法
 */
public abstract class BaseAgentState extends MessagesState<ChatMessage> {

    public BaseAgentState(Map<String, Object> initData) {
        super(initData);
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

    /**
     * 获取原始用户查询
     */
    public Optional<String> getOriginalUserQuery() {
        return this.value("originalUserQuery");
    }

    /**
     * 创建新的状态实例 - 受保护的辅助方法
     */
    protected <T extends BaseAgentState> T withData(String key, Object value, Class<T> stateClass) {
        Map<String, Object> newData = new java.util.HashMap<>(this.data());
        newData.put(key, value);
        try {
            return stateClass.getDeclaredConstructor(Map.class).newInstance(newData);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create new state instance", e);
        }
    }

    /**
     * 创建新的状态实例 - 通用方法
     */
    protected BaseAgentState withData(String key, Object value) {
        Map<String, Object> newData = new java.util.HashMap<>(this.data());
        newData.put(key, value);
        return new BaseAgentState(newData) {
            // 匿名实现，用于通用状态更新
        };
    }
}


