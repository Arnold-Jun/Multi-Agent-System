package com.zhouruojun.travelingagent.agent.actions;

import com.zhouruojun.travelingagent.agent.BaseAgent;
import com.zhouruojun.travelingagent.agent.state.BaseAgentState;
import com.zhouruojun.travelingagent.common.MessageFilter;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;
import org.bsc.async.AsyncGenerator;
import org.bsc.langgraph4j.streaming.StreamingOutput;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;

/**
 * 基础智能体调用类
 * 所有智能体调用的基础类
 */
@Slf4j
public abstract class CallAgent<T extends BaseAgentState> implements NodeAction<T> {

    protected static final int MAX_RETRIES = 3;
    
    protected final BaseAgent agent;
    protected final String agentName;
    protected BlockingQueue<AsyncGenerator.Data<StreamingOutput<T>>> queue;

    public CallAgent(@NonNull String agentName, @NonNull BaseAgent agent) {
        this.agentName = agentName;
        this.agent = agent;
    }

    /**
     * 设置流式输出队列
     */
    public void setQueue(BlockingQueue<AsyncGenerator.Data<StreamingOutput<T>>> queue) {
        this.queue = queue;
    }

    @Override
    public Map<String, Object> apply(T state) {

        try {
            return applyWithRetry(state);
        } catch (Exception e) {
            log.error("Failed to call {} agent: {}", agentName, e);
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("error", e.getMessage());
            return errorResult;
        }
    }

    /**
     * 带重试机制的智能体调用
     */
    protected Map<String, Object> applyWithRetry(T state) {
        int retryCount = 0;
        Exception lastException = null;

        while (retryCount < MAX_RETRIES) {
            try {
                // 构建消息 - 子类实现
                List<ChatMessage> messages = buildMessages(state);
                
                log.info("Calling {} agent (attempt {}/{})", agentName, retryCount + 1, MAX_RETRIES);

                // 调用智能体
                var response = agent.execute(messages);
                AiMessage aiMessage = response.aiMessage();

                // 创建过滤后的AI消息
                AiMessage filteredAiMessage = MessageFilter.createFilteredMessage(aiMessage);

                // 处理响应
                return processResponse(aiMessage, filteredAiMessage, state);

            } catch (Exception e) {
                lastException = e;
                retryCount++;
                log.warn("{} agent call failed (attempt {}/{}): {}", agentName, retryCount, MAX_RETRIES, e.getMessage());
                
                if (retryCount < MAX_RETRIES) {
                    try {
                        Thread.sleep(1000 * retryCount); // 递增延迟
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        throw new RuntimeException("Failed to call " + agentName + " agent after " + MAX_RETRIES + " retries", lastException);
    }

    /**
     * 构建发送给智能体的消息
     * 子类必须实现此方法
     */
    protected abstract List<ChatMessage> buildMessages(T state);

    /**
     * 处理智能体响应
     * 子类必须实现此方法
     */
    protected abstract Map<String, Object> processResponse(AiMessage originalMessage, AiMessage filteredMessage, T state);

    /**
     * 创建错误结果
     * 子类可以重写此方法来实现特定的错误处理逻辑
     */
    protected Map<String, Object> createErrorResult(T state, String errorMessage) {
        Map<String, Object> result = new HashMap<>();
        result.put("error", errorMessage);
        
        // 构建错误消息
        String failureMessage = String.format("%s执行失败: %s。", agentName, errorMessage);
        List<ChatMessage> errorMessages = new ArrayList<>(state.messages());
        errorMessages.add(UserMessage.from(failureMessage));
        result.put("messages", errorMessages);
        
        return result;
    }
}