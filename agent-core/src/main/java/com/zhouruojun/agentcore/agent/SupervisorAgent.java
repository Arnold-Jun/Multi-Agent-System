package com.zhouruojun.agentcore.agent;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;

import java.util.List;

/**
 * <p>
 * SupervisorAgent - 智能分发主管
 * </p>
 *
 */
public class SupervisorAgent extends BaseAgent {

    private final List<String> agentNames;

    @Override
    protected String getPrompt() {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是一名智能分发主管，负责分析用户需求并决定如何处理。\n");
        prompt.append("你有以下处理方式：\n");
        prompt.append("1. directReply - 对于一般性问答、知识性问题、生活建议等，你可以直接回复\n");
        prompt.append("2. agentInvoke - 对于需要专业工具处理的问题，调用相应的专业智能体\n");
        prompt.append("3. userInput - 当需要更多信息或用户交互时使用\n");
        prompt.append("4. FINISH - 当任务完成时使用\n\n");
        
        prompt.append("你的团队由以下专业智能体组成：");
        
        if (agentNames != null && !agentNames.isEmpty()) {
            prompt.append(String.join(", ", agentNames));
        } else {
            prompt.append("暂无可用Agent");
        }
        
        prompt.append("\n\n决策规则：\n");
        prompt.append("1. 对于数据分析、统计计算、图表生成等专业问题 → 选择 agentInvoke\n");
        prompt.append("2. 对于一般性对话、知识问答、生活建议等 → 选择 directReply\n");
        prompt.append("3. 当需要更多信息时 → 选择 userInput\n");
        prompt.append("4. 任务完成时 → 选择 FINISH\n");
        prompt.append("5. 永远不要选择团队成员以外的Agent\n");
        prompt.append("6. 同一个Agent连续调用3次以上视为循环，应终止流程\n\n");
        
        prompt.append("重要：当需要调用专业智能体时，你必须根据整个对话上下文推理出具体的任务指令！\n");
        prompt.append("请根据问题类型决定处理方式：\n");
        prompt.append("1. 如果是简单问题（一般对话、知识问答、生活建议等），直接给出友好回复\n");
        prompt.append("2. 如果需要专业工具处理，请按以下格式回复：\n");
        prompt.append("   \"需要调用专业智能体：{智能体名称}\"\n");
        prompt.append("   \"具体任务指令：{根据对话上下文推理出的具体任务描述}\"\n");
        prompt.append("3. 如果需要更多信息，回复：\"需要更多信息\"\n");
        prompt.append("4. 如果任务完成，回复：\"任务完成\"\n\n");
        
        prompt.append("特别重要：当用户要求你总结或处理专业智能体的分析结果时，你应该：\n");
        prompt.append("- 仔细阅读分析结果\n");
        prompt.append("- 将技术性内容转化为用户友好的回复\n");
        prompt.append("- 总结关键发现和洞察\n");
        prompt.append("- 提供实用建议\n");
        prompt.append("- 直接给出最终回复，不要再说\"需要调用专业智能体\"\n\n");
        
        prompt.append("特别说明：当你收到专业智能体的分析结果时，你需要：\n");
        prompt.append("- 仔细分析专业智能体返回的结果\n");
        prompt.append("- 将技术性的分析结果转化为用户友好的回复\n");
        prompt.append("- 总结关键发现和洞察\n");
        prompt.append("- 提供实用的建议或下一步行动\n");
        prompt.append("- 确保回复简洁明了，易于理解\n");
        prompt.append("- 重要：此时不要再说\"需要调用专业智能体\"，直接给出最终回复\n\n");
        
        prompt.append("示例：\n");
        prompt.append("用户：\"我想分析一下销售数据\"\n");
        prompt.append("回复：\"需要调用专业智能体：data-analysis-agent\"\n");
        prompt.append("      \"具体任务指令：请分析销售数据，包括数据加载、描述性统计分析、趋势分析和可视化展示\"\n\n");
        prompt.append("当收到分析结果后，你需要将技术结果转化为用户友好的回复。\n\n");
        prompt.append("请直接给出自然语言回复，系统会自动判断是否需要调用专业智能体。");

        return prompt.toString();
    }

    public SupervisorAgent(ChatLanguageModel chatLanguageModel,
                           StreamingChatLanguageModel streamingChatLanguageModel,
                           List<ToolSpecification> tools, String agentName, List<String> agentNames) {
        super(chatLanguageModel, streamingChatLanguageModel, tools, agentName);
        this.agentNames = agentNames;
    }

    /**
     * 使用Builder构建SupervisorAgent
     */
    public static class SupervisorAgentBuilder {
        private ChatLanguageModel chatLanguageModel;
        private StreamingChatLanguageModel streamingChatLanguageModel;
        private List<ToolSpecification> tools;
        private String agentName;
        private List<String> agentNames;

        public SupervisorAgentBuilder chatLanguageModel(ChatLanguageModel chatLanguageModel) {
            this.chatLanguageModel = chatLanguageModel;
            return this;
        }

        public SupervisorAgentBuilder streamingChatLanguageModel(StreamingChatLanguageModel streamingChatLanguageModel) {
            this.streamingChatLanguageModel = streamingChatLanguageModel;
            return this;
        }

        public SupervisorAgentBuilder tools(List<ToolSpecification> tools) {
            this.tools = tools;
            return this;
        }

        public SupervisorAgentBuilder agentName(String agentName) {
            this.agentName = agentName;
            return this;
        }

        public SupervisorAgentBuilder agentNames(List<String> agentNames) {
            this.agentNames = agentNames;
            return this;
        }

        public SupervisorAgent build() {
            return new SupervisorAgent(
                chatLanguageModel, 
                streamingChatLanguageModel, 
                tools, 
                agentName, 
                agentNames
            );
        }
    }

    public static SupervisorAgentBuilder supervisorBuilder() {
        return new SupervisorAgentBuilder();
    }
}

