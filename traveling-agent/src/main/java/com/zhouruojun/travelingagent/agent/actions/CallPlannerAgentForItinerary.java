package com.zhouruojun.travelingagent.agent.actions;

import com.zhouruojun.travelingagent.agent.BaseAgent;
import com.zhouruojun.travelingagent.agent.state.SubgraphState;
import com.zhouruojun.travelingagent.prompts.PromptManager;
import dev.langchain4j.data.message.AiMessage;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 行程规划智能体调用节点（用于ItineraryPlanner子图）
 * 负责制定旅游行程和机酒方案，将完整方案作为响应返回
 */
@Slf4j
public class CallPlannerAgentForItinerary extends CallSubAgent {

    /**
     * 构造函数
     *
     * @param agentName 智能体名称
     * @param agent 智能体实例
     * @param promptManager 提示词管理器
     */
    public CallPlannerAgentForItinerary(@NonNull String agentName, @NonNull BaseAgent agent, @NonNull PromptManager promptManager) {
        super(agentName, agent, promptManager);
    }

    @Override
    protected Map<String, Object> processResponse(AiMessage originalMessage, AiMessage filteredMessage, SubgraphState state) {
        // 检查是否有工具调用请求
        if (originalMessage.hasToolExecutionRequests()) {
            log.info("ItineraryPlanner {} requested tool execution: {}", agentName, originalMessage.toolExecutionRequests());
            return Map.of(
                    "messages", List.of(filteredMessage),
                    "toolExecutionRequests", originalMessage.toolExecutionRequests(),
                    "next", "executeTools"
            );
        }

        // 如果没有工具调用，说明已经生成了完整方案
        String responseText = filteredMessage.text();
        log.info("ItineraryPlanner {} 生成了完整方案，长度: {} 字符", agentName, responseText != null ? responseText.length() : 0);

        Map<String, Object> result = new HashMap<>();
        result.put("messages", List.of(filteredMessage));
        
        // 将完整方案存储到状态中，供Supervisor审查使用
        if (responseText != null && !responseText.trim().isEmpty()) {
            result.put("currentPlan", responseText);
        }

        // 路由到supervisor进行审查
        result.put("next", "itinerarySupervisor");
        
        return result;
    }
}
