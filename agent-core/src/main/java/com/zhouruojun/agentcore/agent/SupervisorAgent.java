package com.zhouruojun.agentcore.agent;

import com.zhouruojun.agentcore.common.PromptTemplateManager;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import com.zhouruojun.a2acore.spec.AgentCard;

import java.util.List;

/**
 * <p>
 * SupervisorAgent - 智能分发主管
 * </p>
 *
 */
public class SupervisorAgent extends BaseAgent {

    private final List<AgentCard> agentCards;

    @Override
    protected String getPrompt() {
        return PromptTemplateManager.instance.buildSupervisorPrompt(agentCards);
    }

    /**
     * 构造函数
     */
    private SupervisorAgent(ChatLanguageModel chatLanguageModel,
                           StreamingChatLanguageModel streamingChatLanguageModel,
                           String agentName, 
                           List<AgentCard> agentCards,
                           boolean compactContextEnabled) {
        super(chatLanguageModel, streamingChatLanguageModel, agentName, compactContextEnabled);
        this.agentCards = agentCards;
    }

    /**
     * SupervisorAgent专用Builder
     * 继承BaseAgentBuilder，添加agentCards支持
     */
    public static class SupervisorAgentBuilder extends BaseAgentBuilder<SupervisorAgent, SupervisorAgentBuilder> {
        private List<AgentCard> agentCards;

        public SupervisorAgentBuilder agentCards(List<AgentCard> agentCards) {
            this.agentCards = agentCards;
            return this;
        }

        @Override
        public SupervisorAgent build() {
            return new SupervisorAgent(
                chatLanguageModel, 
                streamingChatLanguageModel, 
                agentName, 
                agentCards,
                compactContextEnabled
            );
        }
    }

    /**
     * 创建SupervisorAgentBuilder实例
     */
    public static SupervisorAgentBuilder supervisorBuilder() {
        return new SupervisorAgentBuilder();
    }
}

