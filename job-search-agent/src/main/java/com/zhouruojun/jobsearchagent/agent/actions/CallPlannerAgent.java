package com.zhouruojun.jobsearchagent.agent.actions;

import com.zhouruojun.jobsearchagent.agent.BaseAgent;
import com.zhouruojun.jobsearchagent.agent.state.MainGraphState;
import com.zhouruojun.jobsearchagent.common.PromptTemplateManager;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

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

    /**
     * 构造函数
     *
     * @param agentName 智能体名称
     * @param agent 智能体实例
     */
    public CallPlannerAgent(@NonNull String agentName, @NonNull BaseAgent agent) {
        super(agentName, agent);
    }

    @Override
    protected List<ChatMessage> buildMessages(MainGraphState state) {
        // 使用PromptTemplateManager构建消息，它会根据状态自动判断场景
        List<ChatMessage> messages = PromptTemplateManager.getInstance().buildPlannerMessages(state);
        
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
            
            // 记录规划结果
            log.debug("规划智能体响应内容: {}", responseText);
            
            return Map.of(
                    "messages", List.of(filteredMessage),
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
