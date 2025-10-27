package com.zhouruojun.travelingagent.agent.actions;

import com.zhouruojun.travelingagent.agent.BaseAgent;
import com.zhouruojun.travelingagent.agent.state.MainGraphState;
import com.zhouruojun.travelingagent.prompts.PromptManager;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 规划智能体调用节点
 * 专门用于主图的规划阶段，负责分析用户需求并制定执行计划
 * 
 * 主要功能：
 * 1. 初始规划：分析用户需求，生成任务列表
 * 2. 重新规划：根据Scheduler的建议和失败原因，重新规划任务
 * 3. JSON解析错误处理：处理TodoListParser的JSON解析错误
 */
@Slf4j
public class CallPlannerAgent extends CallAgent<MainGraphState> {

    private final PromptManager promptManager;

    /**
     * 构造函数
     *
     * @param agentName 智能体名称
     * @param agent 智能体实例
     * @param promptManager 提示词管理器
     */
    public CallPlannerAgent(@NonNull String agentName, @NonNull BaseAgent agent, @NonNull PromptManager promptManager) {
        super(agentName, agent);
        this.promptManager = promptManager;
    }

    @Override
    protected List<ChatMessage> buildMessages(MainGraphState state) {
        // 使用新的提示词管理器构建消息
        List<ChatMessage> messages = promptManager.buildMainGraphMessages(agentName, state);
        
        log.debug("规划智能体消息构建完成，消息数量: {}", messages.size());
        
        return messages;
    }

    @Override
    protected Map<String, Object> processResponse(AiMessage originalMessage, AiMessage filteredMessage, MainGraphState state) {
        try {
            // Planner不应该有工具调用，它只负责生成JSON格式的任务列表
            if (originalMessage.hasToolExecutionRequests()) {
                log.warn("规划智能体 {} 不应该有工具调用请求，忽略工具调用: {}", 
                        agentName, originalMessage.toolExecutionRequests());
            }
            
            // 检查响应内容是否包含有效的JSON
            String responseText = filteredMessage.text();
            if (responseText == null || responseText.trim().isEmpty()) {
                log.error("规划智能体 {} 返回空响应", agentName);
                return createErrorResult(state, "规划智能体返回空响应");
            }
            
            // ✅ 新增：添加当前查询到userQueryHistory
            String currentQuery = getCurrentUserQuery(state);
            List<String> userQueryHistory = new ArrayList<>(state.getUserQueryHistory());
            
            // 检查是否已经添加过（避免重复）
            if (userQueryHistory.isEmpty() || !userQueryHistory.get(userQueryHistory.size() - 1).equals(currentQuery)) {
                userQueryHistory.add(currentQuery);
                log.info("Planner: Added current query to userQueryHistory: {}", currentQuery);
            }
            
            // 如果是第一次调用且没有originalUserQuery，设置它
            if (state.getOriginalUserQuery().isEmpty() && !currentQuery.isEmpty()) {
                log.info("Planner: Setting originalUserQuery for first call: {}", currentQuery);
                return Map.of(
                        "messages", List.of(filteredMessage),
                        "userQueryHistory", userQueryHistory,
                        "originalUserQuery", currentQuery,  // ← 设置originalUserQuery
                        "next", "todoListParser"
                );
            }
            
            // 记录规划结果
            log.debug("规划智能体响应内容: {}", responseText);
            
            return Map.of(
                    "messages", List.of(filteredMessage),
                    "userQueryHistory", userQueryHistory,  // ← 返回更新后的历史
                    "next", "todoListParser"
            );
            
        } catch (Exception e) {
            log.error("规划智能体 {} 处理响应失败: {}", agentName, e.getMessage(), e);
            
            // 发生错误时，创建错误消息并路由到todoListParser
            // todoListParser会检测到JSON解析错误并路由回planner
            String errorMessage = String.format("规划智能体处理失败: %s", e.getMessage());
            AiMessage errorAiMessage = AiMessage.from(errorMessage);
            
            return Map.of(
                    "messages", List.of(errorAiMessage),
                    "next", "todoListParser"
            );
        }
    }
    
    /**
     * 获取当前用户查询
     */
    private String getCurrentUserQuery(MainGraphState state) {
        // 从messages中获取最新的UserMessage
        return state.messages().stream()
                .filter(msg -> msg instanceof dev.langchain4j.data.message.UserMessage)
                .map(msg -> ((dev.langchain4j.data.message.UserMessage) msg).singleText())
                .reduce((first, second) -> second) // 获取最后一个
                .orElse(state.getOriginalUserQuery().orElse(""));
    }

    /**
     * 创建错误结果
     */
    protected Map<String, Object> createErrorResult(MainGraphState state, String errorMessage) {
        log.error("规划智能体错误: {}", errorMessage);
        
        // 创建包含错误信息的AiMessage
        AiMessage errorAiMessage = AiMessage.from(errorMessage);
        
        return Map.of(
                "messages", List.of(errorAiMessage),
                "next", "todoListParser"
        );
    }
}
