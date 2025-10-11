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
 * 子图总结智能体调用节点
 * 专门用于子图执行完成后的总结，为完成用户原始查询的主任务提供必要的信息
 */
@Slf4j
public class CallSubSummaryAgent extends CallAgent<SubgraphState> {

    /**
     * 构造函数
     *
     * @param agentName 智能体名称
     * @param agent 智能体实例
     */
    public CallSubSummaryAgent(@NonNull String agentName, @NonNull BaseAgent agent) {
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
        
        // 获取系统提示词
        String systemPrompt = PromptTemplateManager.getInstance().getSubgraphSummaryPrompt();
        
        // 构建用户提示词
        String userPrompt = buildUserPromptForSummary(currentTask, state);
        
        messages.add(SystemMessage.from(systemPrompt));
        messages.add(UserMessage.from(userPrompt));
        
        log.debug("子图总结消息构建完成，系统提示词长度: {}, 用户提示词长度: {}", 
                systemPrompt.length(), userPrompt.length());
        
        return messages;
    }

    @Override
    protected Map<String, Object> processResponse(AiMessage originalMessage, AiMessage filteredMessage, SubgraphState state) {
        // 子图总结智能体不需要工具调用，直接返回总结结果
        log.info("Subgraph summary agent {} responded: {}", agentName, filteredMessage.text());
        return Map.of(
                "messages", List.of(filteredMessage),
                "finalResponse", filteredMessage.text()
        );
    }

    /**
     * 构建子图总结的用户提示词
     */
    private String buildUserPromptForSummary(TodoTask currentTask, SubgraphState state) {
        // 获取用户原始查询
        String originalQuery = state.getOriginalUserQuery().orElse("未知查询");
        
        // 获取任务描述
        String taskDescription = currentTask.getDescription();
        
        // 获取工具执行结果
        String toolExecutionResult = state.getToolExecutionResult().orElse("无工具执行结果");
        
        // 使用PromptTemplateManager构建用户提示词
        return PromptTemplateManager.getInstance().buildSubgraphSummaryUserPrompt(
                originalQuery, 
                taskDescription, 
                toolExecutionResult
        );
    }
}
