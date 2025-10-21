package com.zhouruojun.travelingagent.agent.actions;

import com.zhouruojun.travelingagent.agent.BaseAgent;
import com.zhouruojun.travelingagent.agent.state.MainGraphState;
import com.zhouruojun.travelingagent.common.PromptTemplateManager;
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
import java.util.Optional;

/**
 * 总结智能体调用节点
 * 负责汇总所有子智能体的执行结果，生成最终报告
 */
@Slf4j
public class CallSummaryAgent extends CallAgent<MainGraphState> {

    /**
     * 构造函数
     *
     * @param agentName 智能体名称
     * @param agent 智能体实例
     */
    public CallSummaryAgent(@NonNull String agentName, @NonNull BaseAgent agent) {
        super(agentName, agent);
    }

    @Override
    protected List<ChatMessage> buildMessages(MainGraphState state) {
        List<ChatMessage> messages = new ArrayList<>();
        
        // 构建系统提示词
        String systemPrompt = PromptTemplateManager.instance.buildSummarySystemPrompt();
        messages.add(SystemMessage.from(systemPrompt));
        
        // 构建用户提示词
        String userPrompt = buildUserPrompt(state);
        messages.add(UserMessage.from(userPrompt));
        
        log.debug("Summary智能体消息构建完成，消息数量: {}", messages.size());
        
        return messages;
    }

    @Override
    protected Map<String, Object> processResponse(AiMessage originalMessage, AiMessage filteredMessage, MainGraphState state) {
        try {
            String aiResponse = filteredMessage.text();
            log.info("Summary response: {}", aiResponse);
            
            // 设置最终响应
            state.setFinalResponse(aiResponse);
            
            // 添加AI响应到消息历史
            state.addMessage(filteredMessage);
            
            // 构建返回结果
            Map<String, Object> result = new HashMap<>();
            result.put("messages", state.messages());
            result.put("finalResponse", aiResponse);
            
            log.info("Summary处理完成，生成最终报告");
            
            return result;
            
        } catch (Exception e) {
            log.error("Summary响应处理失败", e);
            
            // 创建错误结果
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("error", "Summary响应处理失败: " + e.getMessage());
            
            return errorResult;
        }
    }
    
    /**
     * 构建用户提示词
     */
    private String buildUserPrompt(MainGraphState state) {
        String originalUserQuery = state.getOriginalUserQuery().orElse("请生成旅游规划报告");
        String subgraphResultsInfo = formatSubgraphResults(state);
        
        return PromptTemplateManager.instance.buildSummaryUserPrompt(
            originalUserQuery, subgraphResultsInfo
        );
    }
    
    /**
     * 格式化子图结果
     */
    private String formatSubgraphResults(MainGraphState state) {
        Optional<Map<String, String>> subgraphResultsOpt = state.getSubgraphResults();
        if (subgraphResultsOpt.isEmpty() || subgraphResultsOpt.get().isEmpty()) {
            return "暂无子图执行结果";
        }
        
        Map<String, String> subgraphResults = subgraphResultsOpt.get();
        StringBuilder info = new StringBuilder();
        subgraphResults.forEach((agent, result) -> 
            info.append(String.format("**%s执行结果**：\n%s\n\n", agent, result))
        );
        return info.toString();
    }
}