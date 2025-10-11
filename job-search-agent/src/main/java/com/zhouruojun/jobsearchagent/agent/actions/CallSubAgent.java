package com.zhouruojun.jobsearchagent.agent.actions;

import com.zhouruojun.jobsearchagent.agent.BaseAgent;
import com.zhouruojun.jobsearchagent.agent.state.SubgraphState;
import com.zhouruojun.jobsearchagent.agent.state.main.TodoTask;
import com.zhouruojun.jobsearchagent.common.PromptTemplateManager;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 子图智能体调用节点
 * 专门用于子图的简历分析、岗位搜索、匹配度分析等节点
 * 支持工具调用，专注于具体的求职任务执行
 */
@Slf4j
public class CallSubAgent extends CallAgent<SubgraphState> {

    /**
     * 构造函数
     *
     * @param agentName 智能体名称
     * @param agent 智能体实例
     */
    public CallSubAgent(@NonNull String agentName, @NonNull BaseAgent agent) {
        super(agentName, agent);
    }

    @Override
    protected List<ChatMessage> buildMessages(SubgraphState state) {
        List<ChatMessage> messages = new ArrayList<>();
        
        // 获取当前任务
        Optional<TodoTask> currentTaskOpt = state.getCurrentTask();
        if (!currentTaskOpt.isPresent()) {
            log.warn("子图状态中没有当前任务，使用原始消息");
            return state.messages();
        }
        
        TodoTask currentTask = currentTaskOpt.get();
        
        // 获取子图类型
        String subgraphType = state.getSubgraphType().orElse("unknown");

        // 根据子图类型获取对应的提示词
        String systemPrompt = getSystemPromptForSubgraphType(subgraphType);
        String userPrompt = buildUserPromptForSubgraph(currentTask, state);
        
        messages.add(SystemMessage.from(systemPrompt));
        messages.add(UserMessage.from(userPrompt));

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
                    "next", "summary"
            );
        }
    }

    /**
     * 根据子图类型获取系统提示词
     */
    private String getSystemPromptForSubgraphType(String subgraphType) {
        return PromptTemplateManager.getInstance().buildSubgraphSystemPrompt(subgraphType);
    }

    /**
     * 构建用户提示词
     */
    private String buildUserPromptForSubgraph(TodoTask currentTask, SubgraphState state) {
        log.info("工具消息： {}", state.getToolExecutionResult());
        return PromptTemplateManager.getInstance().buildSubgraphUserPrompt(
            currentTask.getDescription(),  // 已经是最新的任务描述
            state.getSubgraphContext().orElse(null),  // Scheduler的上下文
            state.getToolExecutionResult().orElse(null),
            state.getSubgraphType().orElse("unknown")
        );
    }

}
