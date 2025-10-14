package com.zhouruojun.jobsearchagent.agent.actions;

import com.zhouruojun.jobsearchagent.agent.BaseAgent;
import com.zhouruojun.jobsearchagent.agent.state.MainGraphState;
import com.zhouruojun.jobsearchagent.common.PromptTemplateManager;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Summary智能体调用节点
 * 负责调用Summary智能体，生成最终的求职分析报告
 * 注意：Planner和Scheduler现在使用专门的CallPlannerAgent和CallSchedulerAgent
 */
@Slf4j
public class CallSummaryAgent extends CallAgent<MainGraphState> {

    public CallSummaryAgent(@NonNull String agentName, @NonNull BaseAgent agent) {
        super(agentName, agent);
    }

    @Override
    protected List<ChatMessage> buildMessages(MainGraphState state) {
        // CallSummaryAgent专门处理summary节点
        if ("summary".equals(agentName)) {
            return buildSummaryMessages(state);
        } else {
            throw new IllegalArgumentException("CallSummaryAgent只支持summary节点，未知的智能体: " + agentName);
        }
    }

    @Override
    protected Map<String, Object> processResponse(AiMessage originalMessage, AiMessage filteredMessage, MainGraphState state) {
        // Summary节点直接返回过滤后的消息
        Map<String, Object> result = new HashMap<>();
        result.put("messages", List.of(filteredMessage));
        
        // 设置最终响应
        result.put("finalResponse", filteredMessage.text());
        
        return result;
    }


    /**
     * 构建Summary的消息
     * 使用PromptTemplateManager的专用System/User拆分方法
     */
    private List<ChatMessage> buildSummaryMessages(MainGraphState state) {
        List<ChatMessage> messages = new ArrayList<>();
        
        // 获取动态数据
        String originalUserQuery = state.getOriginalUserQuery().orElse("用户查询");
        Map<String, String> subgraphResults = state.getSubgraphResults().orElse(new HashMap<>());
        
        // 构建筑图结果信息
        StringBuilder subgraphResultsInfo = new StringBuilder();
        if (!subgraphResults.isEmpty()) {
            subgraphResults.forEach((agent, result) -> {
                subgraphResultsInfo.append(agent).append(": ").append(result).append("\n");
            });
        } else {
            subgraphResultsInfo.append("暂无子图执行结果");
        }
        
        String systemPrompt = PromptTemplateManager.getInstance().buildSummarySystemPrompt();
        String userPrompt = PromptTemplateManager.getInstance().buildSummaryUserPrompt(originalUserQuery, subgraphResultsInfo.toString());
        messages.add(SystemMessage.from(systemPrompt));
        messages.add(UserMessage.from(userPrompt));
        
        return messages;
    }
}