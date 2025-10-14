package com.zhouruojun.agentcore.common;

import com.zhouruojun.a2acore.spec.AgentCard;
import com.zhouruojun.a2acore.spec.AgentSkill;
import lombok.Getter;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.ArrayList;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.message.AiMessage;

/**
 * 智能体核心提示词模板管理器
 * 统一管理所有智能体的提示词模板
 * 使用单例模式
 */
@Component
public class PromptTemplateManager {
    
    @Getter
    public static final PromptTemplateManager instance = new PromptTemplateManager();
    
    private PromptTemplateManager() {
    }
    
    
    /**
     * 构建动态主管智能体提示词
     * @param agentCards 智能体卡片列表
     * @return 动态生成的提示词
     */
    public String buildSupervisorPrompt(List<AgentCard> agentCards) {
        return String.format("""
            你是一名智能分发主管，负责分析用户需求并决定如何处理。
            
            你有以下处理方式：
            1. agentInvoke - 对于需要专业工具处理的问题，调用相应的专业智能体
            2. FINISH - 当任务完成时使用
            
            你的团队由以下专业智能体组成：
            %s
            
            决策规则：
            1. 根据上述智能体的技能描述，判断用户请求是否需要专业处理 → 选择 agentInvoke
            2. 对于一般性对话、知识问答、生活建议等 → 选择 FINISH
            3. 任务完成时 → 选择 FINISH
            4. 永远不要选择团队成员以外的Agent
            5. 同一个Agent连续调用3次以上视为循环，应终止流程
            
            重要提醒：
            - 根据上述智能体的技能描述，选择合适的智能体处理用户请求
            - 对于需要专业工具处理的任务，优先考虑调用相应的专业智能体
            - 仔细分析用户需求，匹配最合适的智能体技能
            
            特别重要：你必须严格按照以下JSON格式输出响应，不要添加任何其他内容：
            
            如果需要调用专业智能体，输出格式：
            {
                "next": "agentInvoke",
                "targetAgent": "智能体名称",
                "message": "根据智能体技能描述和用户的输入推理出的具体任务指令"
            }
            
            如果直接回复用户或任务完成，输出格式：
            {
                "next": "FINISH", 
                "message": "给用户的友好回复内容"
            }
            
            示例：
            用户："我想分析一下销售数据"
            输出：{"next": "agentInvoke", "targetAgent": "data-analysis-agent", "message": "分析销售数据，生成统计报告和可视化图表"}
            
            用户："帮我找一份Java开发工作"
            输出：{"next": "agentInvoke", "targetAgent": "job-search-agent", "message": "搜索Java开发工程师职位，匹配用户技能和经验"}
            
            用户："你好"
            输出：{"next": "FINISH", "message": "你好！我是智能分发主管，可以帮助您协调各种专业智能体完成任务。请告诉我您需要什么帮助？"}
            
            重要：你必须严格按照JSON格式输出，不要添加任何解释或其他文字！
            """, buildAgentCardsDescription(agentCards));
    }

    /**
     * - SystemMessage：由调用方传入的 systemPrompt
     * - UserMessage：依据历史对话压缩生成，包含用户输入和任务执行结果
     */
    public List<ChatMessage> buildCompactMessages(List<ChatMessage> historyMessages, String systemPrompt) {
        List<ChatMessage> compact = new ArrayList<>(2);

        // 1) 固定放置一条系统提示
        compact.add(SystemMessage.from(systemPrompt));

        // 2) 将历史压缩为一条用户消息
        StringBuilder userContext = new StringBuilder();
        userContext.append("以下是对话历史和执行结果，请基于此理解并继续：\n\n");

        if (historyMessages != null) {
            for (ChatMessage msg : historyMessages) {
                if (msg instanceof UserMessage um) {
                    String text = um.singleText();
                    if (text != null && !text.isBlank()) {
                        userContext.append("用户输入: ").append(text).append('\n');
                    }
                } else if (msg instanceof AiMessage am) {
                    String text = am.text();
                    if (text != null && !text.isBlank()) {
                        userContext.append("任务执行结果: ").append(trimForContext(text)).append('\n');
                    }
                }
            }
        }

        if (userContext.length() == 0) {
            userContext.append("(无历史上下文)");
        }

        compact.add(UserMessage.from(userContext.toString()));
        return compact;
    }

    private String trimForContext(String text) {
        final int limit = 800; // 简单截断，避免过长提示词
        if (text == null) return "";
        return text.length() <= limit ? text : text.substring(0, limit) + "...";
    }
    
    /**
     * 构建智能体卡片描述
     * @param agentCards 智能体卡片列表
     * @return 智能体描述字符串
     */
    private String buildAgentCardsDescription(List<AgentCard> agentCards) {
        if (agentCards == null || agentCards.isEmpty()) {
            return "- 暂无可用Agent\n";
        }
        
        StringBuilder description = new StringBuilder();
        for (AgentCard agentCard : agentCards) {
            description.append("- ").append(agentCard.getName()).append(": ");
            
            if (agentCard.getDescription() != null) {
                description.append(agentCard.getDescription());
            } else {
                description.append("专业智能体");
            }
            description.append("\n");
            
            // 添加技能信息
            if (agentCard.getSkills() != null && !agentCard.getSkills().isEmpty()) {
                description.append("  技能包括：");
                for (int i = 0; i < agentCard.getSkills().size(); i++) {
                    AgentSkill skill = agentCard.getSkills().get(i);
                    description.append(skill.getName());
                    if (skill.getDescription() != null) {
                        description.append("(").append(skill.getDescription()).append(")");
                    }
                    if (i < agentCard.getSkills().size() - 1) {
                        description.append(", ");
                    }
                }
                description.append("\n");
            }
            description.append("\n");
        }
        
        return description.toString();
    }
}
