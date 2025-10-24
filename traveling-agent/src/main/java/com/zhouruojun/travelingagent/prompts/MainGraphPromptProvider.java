package com.zhouruojun.travelingagent.prompts;

import dev.langchain4j.data.message.ChatMessage;
import com.zhouruojun.travelingagent.agent.state.MainGraphState;

import java.util.List;

/**
 * 主图提示词提供者接口
 * 专门用于主图节点的提示词管理
 */
public interface MainGraphPromptProvider extends PromptProvider {
    
    /**
     * 构建主图智能体的消息
     * @param state 主图状态
     * @return 构建好的消息列表
     */
    List<ChatMessage> buildMessages(MainGraphState state);
    
    /**
     * 获取系统提示词
     * @param scenario 场景名称
     * @return 系统提示词
     */
    String getSystemPrompt(String scenario);
    
    /**
     * 构建用户提示词
     * @param scenario 场景名称
     * @param state 主图状态
     * @return 用户提示词
     */
    String buildUserPrompt(String scenario, MainGraphState state);
}
