package com.zhouruojun.travelingagent.agent.actions;

import com.zhouruojun.travelingagent.agent.BaseAgent;
import com.zhouruojun.travelingagent.agent.state.SubgraphState;
import com.zhouruojun.travelingagent.prompts.PromptManager;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import org.bsc.async.AsyncGenerator;
import org.bsc.langgraph4j.streaming.StreamingOutput;

/**
 * 子图智能体调用节点
 * 专门用于子图的旅游信息收集、计划优化、预订执行等节点
 * 支持工具调用，专注于具体的旅游任务执行
 */
@Slf4j
public class CallSubAgent extends CallAgent<SubgraphState> {

    @SuppressWarnings("unused")
    private BlockingQueue<AsyncGenerator.Data<StreamingOutput<SubgraphState>>> queue;
    private final PromptManager promptManager;

    /**
     * 构造函数
     *
     * @param agentName 智能体名称
     * @param agent 智能体实例
     * @param promptManager 提示词管理器
     */
    public CallSubAgent(@NonNull String agentName, @NonNull BaseAgent agent, @NonNull PromptManager promptManager) {
        super(agentName, agent);
        this.promptManager = promptManager;
    }

    /**
     * 设置流式输出队列
     */
    public void setQueue(BlockingQueue<AsyncGenerator.Data<StreamingOutput<SubgraphState>>> queue) {
        this.queue = queue;
    }

    @Override
    protected List<ChatMessage> buildMessages(SubgraphState state) {
        // 使用新的提示词管理器构建消息
        List<ChatMessage> messages = promptManager.buildSubgraphMessages(agentName, state);
        
        log.debug("子图智能体 {} 消息构建完成，消息数量: {}", agentName, messages.size());
        
        return messages;
    }

    @Override
    protected Map<String, Object> processResponse(AiMessage originalMessage, AiMessage filteredMessage, SubgraphState state) {
        // 检查是否有工具调用请求
        if (originalMessage.hasToolExecutionRequests()) {
            log.info("Subgraph agent {} requested tool execution: {}", agentName, originalMessage.toolExecutionRequests());
            return Map.of(
                    "messages", List.of(filteredMessage),
                    "toolExecutionRequests", originalMessage.toolExecutionRequests(),
                    "next", "action"
            );
        } else {
            log.info("Subgraph agent {} completed task: {}", agentName, filteredMessage.text());
            return Map.of(
                    "messages", List.of(filteredMessage),
                    "finalResponse", filteredMessage.text(),
                    "next", "END"
            );
        }
    }


}