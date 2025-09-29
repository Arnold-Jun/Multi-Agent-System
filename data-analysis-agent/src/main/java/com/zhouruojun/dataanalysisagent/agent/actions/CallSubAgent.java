package com.zhouruojun.dataanalysisagent.agent.actions;

import com.zhouruojun.dataanalysisagent.agent.BaseAgent;
import com.zhouruojun.dataanalysisagent.agent.state.SubgraphState;
import com.zhouruojun.dataanalysisagent.common.MessageFilter;
import dev.langchain4j.data.message.AiMessage;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.bsc.async.AsyncGenerator;
import org.bsc.langgraph4j.action.NodeAction;
import org.bsc.langgraph4j.streaming.StreamingOutput;

import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;

/**
 * 子图智能体调用节点
 * 专门用于子图的统计分析、数据可视化、网络搜索等节点
 * 支持工具调用，专注于具体的数据分析任务执行
 */
@Slf4j
public class CallSubAgent implements NodeAction<SubgraphState> {

    /**
     * 智能体名称
     */
    final String agentName;

    /**
     * 智能体实例
     */
    final BaseAgent agent;

    /**
     * 流式输出队列
     */
    @SuppressWarnings("unused")
    private BlockingQueue<AsyncGenerator.Data<StreamingOutput<SubgraphState>>> queue;

    /**
     * 构造函数
     *
     * @param agentName 智能体名称
     * @param agent 智能体实例
     */
    public CallSubAgent(@NonNull String agentName, @NonNull BaseAgent agent) {
        this.agentName = agentName;
        this.agent = agent;
    }

    /**
     * 设置流式输出队列
     *
     * @param queue 队列
     */
    public void setQueue(BlockingQueue<AsyncGenerator.Data<StreamingOutput<SubgraphState>>> queue) {
        this.queue = queue;
    }

    /**
     * 应用智能体调用逻辑
     *
     * @param state 当前状态
     * @return 包含智能体响应的映射
     */
    @Override
    public Map<String, Object> apply(SubgraphState state) {
        log.info("Calling subgraph agent: {}", agentName);

        return applyWithRetry(state);
    }

    /**
     * 带重试机制的智能体调用
     */
    private Map<String, Object> applyWithRetry(SubgraphState state) {
        // 子图智能体统一重试策略
        int maxRetries = 3;
        int retryCount = 0;
        Exception lastException = null;

        while (retryCount < maxRetries) {
            try {
                // 获取消息列表
                var messages = state.messages();

                // 调用智能体
                var response = agent.execute(messages);

                // 获取AI消息
                AiMessage aiMessage = response.aiMessage();
                
                // 创建过滤后的AI消息，使用MessageFilter
                AiMessage filteredAiMessage = MessageFilter.createSubGraphFilteredMessage(aiMessage);

                // 检查是否有工具调用请求
                if (aiMessage.hasToolExecutionRequests()) {
                    log.info("Subgraph agent {} requested tool execution: {}", agentName, aiMessage.toolExecutionRequests());
                    return Map.of(
                            "messages", List.of(filteredAiMessage),
                            "toolExecutionRequests", aiMessage.toolExecutionRequests()
                    );
                } else {
                    // 对于没有工具调用的响应，直接返回finalResponse
                    log.info("Subgraph agent {} responded: {}", agentName, filteredAiMessage.text());
                    return Map.of(
                            "messages", List.of(filteredAiMessage),
                            "finalResponse", filteredAiMessage.text()
                    );
                }

            } catch (Exception e) {
                lastException = e;
                retryCount++;
                log.warn("Subgraph agent {} 调用失败，重试 {}/{}: {}", agentName, retryCount, maxRetries, e.getMessage());
                
                if (retryCount < maxRetries) {
                    try {
                        Thread.sleep(1000L * retryCount); // 递增延迟
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        // 重试失败，抛出异常
        log.error("Subgraph agent {} 调用失败，已达到最大重试次数 {}", agentName, maxRetries, lastException);
        throw new RuntimeException("Failed to call subgraph agent: " + agentName + " after " + maxRetries + " retries", lastException);
    }
    
}
