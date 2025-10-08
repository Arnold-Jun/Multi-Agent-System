package com.xiaohongshu.codewiz.codewizagent.agentcore.agent;

import com.xiaohongshu.codewiz.a2acore.client.A2AClient;
import com.xiaohongshu.codewiz.a2acore.spec.AgentCard;
import com.xiaohongshu.codewiz.codewizagent.agentcore.common.PromptParseToObject;
import com.xiaohongshu.codewiz.codewizagent.agentcore.common.PromptTemplateReader;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import java.util.List;

/**
 * <p>
 * SupervisorAgent
 * </p>
 *
 * @author 瑞诺
 * create on 2025/3/26 21:25
 */
public class SupervisorAgent extends BaseAgent {

    private List<AgentCard> agentCards;

    @Override
    protected String getPrompt() {
        return PromptTemplateReader.getInstance()
                .getSupervisorPromptWithAgentCards(agentName, agentCards);
    }

    public SupervisorAgent(ChatLanguageModel chatLanguageModel,
                           StreamingChatLanguageModel streamingChatLanguageModel,
                           List<ToolSpecification> tools, String agentName, List<AgentCard> agentCards) {
        super(chatLanguageModel, streamingChatLanguageModel, tools, agentName);
        this.agentCards = agentCards;
    }
}
