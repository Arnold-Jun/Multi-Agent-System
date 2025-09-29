package com.zhouruojun.agentcore.common;

import com.zhouruojun.a2acore.spec.AgentCard;
import com.zhouruojun.a2acore.spec.AgentSkill;
import lombok.Getter;
import org.springframework.stereotype.Component;

import java.util.List;

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
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是一名智能分发主管，负责分析用户需求并决定如何处理。\n");
        prompt.append("\n");
        prompt.append("你有以下处理方式：\n");
        prompt.append("1. directReply - 对于一般性问答、知识性问题、生活建议等，你可以直接回复\n");
        prompt.append("2. agentInvoke - 对于需要专业工具处理的问题，调用相应的专业智能体\n");
        prompt.append("3. userInput - 当需要更多信息或用户交互时使用\n");
        prompt.append("4. FINISH - 当任务完成时使用\n");
        prompt.append("\n");
        
        prompt.append("你的团队由以下专业智能体组成：\n");
        
        if (agentCards != null && !agentCards.isEmpty()) {
            for (AgentCard agentCard : agentCards) {
                prompt.append("- ").append(agentCard.getName()).append(": ");
                if (agentCard.getDescription() != null) {
                    prompt.append(agentCard.getDescription()).append("\n");
                } else {
                    prompt.append("专业智能体\n");
                }
                
                // 添加技能信息
                if (agentCard.getSkills() != null && !agentCard.getSkills().isEmpty()) {
                    prompt.append("  技能包括：");
                    for (int i = 0; i < agentCard.getSkills().size(); i++) {
                        AgentSkill skill = agentCard.getSkills().get(i);
                        prompt.append(skill.getName());
                        if (skill.getDescription() != null) {
                            prompt.append("(").append(skill.getDescription()).append(")");
                        }
                        if (i < agentCard.getSkills().size() - 1) {
                            prompt.append(", ");
                        }
                    }
                    prompt.append("\n");
                }
                prompt.append("\n");
            }
        } else {
            prompt.append("- 暂无可用Agent\n");
        }
        
        prompt.append("决策规则：\n");
        prompt.append("1. 根据上述智能体的技能描述，判断用户请求是否需要专业处理 → 选择 agentInvoke\n");
        prompt.append("2. 对于一般性对话、知识问答、生活建议等 → 选择 directReply\n");
        prompt.append("3. 当需要更多信息时 → 选择 userInput\n");
        prompt.append("4. 任务完成时 → 选择 FINISH\n");
        prompt.append("5. 永远不要选择团队成员以外的Agent\n");
        prompt.append("6. 同一个Agent连续调用3次以上视为循环，应终止流程\n");
        prompt.append("\n");
        
        prompt.append("重要提醒：\n");
        prompt.append("- 根据上述智能体的技能描述，选择合适的智能体处理用户请求\n");
        prompt.append("- 对于需要专业工具处理的任务，优先考虑调用相应的专业智能体\n");
        prompt.append("- 仔细分析用户需求，匹配最合适的智能体技能\n");
        prompt.append("\n");
        
        prompt.append("重要：当需要调用专业智能体时，你必须根据整个对话上下文推理出具体的任务指令！\n");
        prompt.append("请根据问题类型决定处理方式：\n");
        prompt.append("1. 如果是简单问题（一般对话、知识问答、生活建议等），直接给出友好回复\n");
        prompt.append("2. 如果需要专业工具处理，请按以下格式回复：\n");
        prompt.append("   \"需要调用专业智能体：{智能体名称}\"\n");
        prompt.append("   \"具体任务指令：{根据智能体技能描述推理出的具体任务}\"\n");
        prompt.append("\n");
        
        prompt.append("特别重要：当用户要求你总结或处理专业智能体的分析结果时，你应该：\n");
        prompt.append("- 仔细阅读分析结果\n");
        prompt.append("- 将技术性内容转化为用户友好的回复\n");
        prompt.append("- 总结关键发现和洞察\n");
        prompt.append("- 提供实用建议\n");
        prompt.append("- 直接给出最终回复，不要再说\"需要调用专业智能体\"\n");
        prompt.append("\n");
        
        prompt.append("特别说明：当你收到专业智能体的分析结果时，你需要：\n");
        prompt.append("- 仔细分析专业智能体返回的结果\n");
        prompt.append("- 将技术性的分析结果转化为用户友好的回复\n");
        prompt.append("- 总结关键发现和洞察\n");
        prompt.append("- 提供实用的建议或下一步行动\n");
        prompt.append("- 确保回复简洁明了，易于理解\n");
        prompt.append("- 重要：此时不要再说\"需要调用专业智能体\"，直接给出最终回复\n");
        prompt.append("\n");
        
        prompt.append("示例：\n");
        prompt.append("用户：\"我想分析一下销售数据\"\n");
        prompt.append("回复：\"AgentInvoke：{智能体名称}\"\n");
        prompt.append("      \"具体任务指令：{根据智能体技能描述推理出的具体任务}\"\n");
        prompt.append("\n");
        prompt.append("当收到分析结果后，你需要将技术结果转化为用户友好的回复。\n");
        prompt.append("\n");
        prompt.append("请直接给出自然语言回复，系统会自动判断是否需要调用专业智能体。");
        
        return prompt.toString();
    }
}
