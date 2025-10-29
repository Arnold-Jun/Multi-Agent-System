package com.zhouruojun.travelingagent.agent.actions;

import com.zhouruojun.travelingagent.agent.BaseAgent;
import com.zhouruojun.travelingagent.agent.state.MainGraphState;
import com.zhouruojun.travelingagent.prompts.PromptManager;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 总结智能体调用节点
 * 负责汇总所有子智能体的执行结果，生成最终报告
 */
@Slf4j
public class CallSummaryAgent extends CallAgent<MainGraphState> {

    private final PromptManager promptManager;

    /**
     * 构造函数
     *
     * @param agentName 智能体名称
     * @param agent 智能体实例
     * @param promptManager 提示词管理器
     */
    public CallSummaryAgent(@NonNull String agentName, @NonNull BaseAgent agent, @NonNull PromptManager promptManager) {
        super(agentName, agent);
        this.promptManager = promptManager;
    }

    @Override
    protected List<ChatMessage> buildMessages(MainGraphState state) {
        // 使用新的提示词管理器构建消息
        List<ChatMessage> messages = promptManager.buildMainGraphMessages(agentName, state);
        
        log.debug("总结智能体消息构建完成，消息数量: {}", messages.size());
        
        return messages;
    }

    @Override
    protected Map<String, Object> processResponse(AiMessage originalMessage, AiMessage filteredMessage, MainGraphState state) {
        try {
            String aiResponse = filteredMessage.text();
            log.info("Summary response: {}", aiResponse);
            
            // ✅ 新增：保存finalResponse到历史
            List<String> taskResponseHistory = new ArrayList<>(state.getTaskResponseHistory());
            taskResponseHistory.add(aiResponse);
            log.info("Summary: Added finalResponse to taskResponseHistory: {}", aiResponse);

            // 添加AI响应到消息历史
            state.addMessage(filteredMessage);
            
            // 构建返回结果
            Map<String, Object> result = new HashMap<>();
            result.put("messages", state.messages());
            result.put("finalResponse", aiResponse);
            result.put("taskResponseHistory", taskResponseHistory);  // ← 返回更新后的历史
            
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
    
}