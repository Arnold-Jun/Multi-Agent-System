package com.zhouruojun.travelingagent.prompts;

import dev.langchain4j.data.message.ChatMessage;
import com.zhouruojun.travelingagent.agent.state.SubgraphState;

import java.util.List;

/**
 * 子图提示词提供者接口
 * 专门用于子图节点的提示词管理
 */
public interface SubgraphPromptProvider extends PromptProvider {
    
    /**
     * 构建子图智能体的消息
     * @param state 子图状态
     * @return 构建好的消息列表
     */
    List<ChatMessage> buildMessages(SubgraphState state);
    
    /**
     * 获取系统提示词
     * @return 系统提示词
     */
    String getSystemPrompt();
    
    /**
     * 构建用户提示词
     * @param state 子图状态
     * @return 用户提示词
     */
    String buildUserPrompt(SubgraphState state);
}
