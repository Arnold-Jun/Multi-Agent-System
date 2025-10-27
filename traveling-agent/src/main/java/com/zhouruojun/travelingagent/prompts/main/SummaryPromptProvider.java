package com.zhouruojun.travelingagent.prompts.main;

import com.zhouruojun.travelingagent.agent.state.MainGraphState;
import com.zhouruojun.travelingagent.prompts.BasePromptProvider;
import com.zhouruojun.travelingagent.prompts.MainGraphPromptProvider;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Summary智能体提示词提供者
 * 负责总结阶段的提示词管理
 */
@Slf4j
@Component
public class SummaryPromptProvider extends BasePromptProvider implements MainGraphPromptProvider {
    
    public SummaryPromptProvider() {
        super("summary", List.of("DEFAULT"));
    }
    
    @Override
    public List<ChatMessage> buildMessages(MainGraphState state) {
        logPromptBuilding("DEFAULT", "Building summary messages");
        
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(getSystemPrompt("DEFAULT")));
        messages.add(UserMessage.from(buildUserPrompt("DEFAULT", state)));
        
        return messages;
    }
    
    @Override
    public String getSystemPrompt(String scenario) {
        validateScenario(scenario);
        return buildSummarySystemPrompt();
    }
    
    @Override
    public String buildUserPrompt(String scenario, MainGraphState state) {
        validateScenario(scenario);
        return buildSummaryUserPrompt(state);
    }
    
    /**
     * 构建总结系统提示词
     */
    private String buildSummarySystemPrompt() {
        String basePrompt = """
        你是一名专业的旅行规划师，负责整合所有子智能体的执行结果，生成最终的旅行计划报告。

        **你的核心职责**：
        1. **结果整合**：汇总所有子智能体的执行结果
        2. **信息验证**：确保每个推荐项目都有明确的依据
        3. **报告生成**：生成结构化的旅行计划文档
        4. **质量保证**：确保信息的准确性和完整性

        **重要约束**：
        - **严格基于执行结果**：所有信息必须来源于子智能体的实际执行结果
        - **禁止编造信息**：不得使用训练数据或常识补充任何信息
        - **明确标注来源**：每个推荐项目都要说明其依据来源
        - **信息不足处理**：对于缺失的信息，必须明确标注"信息不足"或"需要进一步收集"

        **报告结构要求**：
        1. **执行摘要**：简要概述整个旅行计划
        2. **详细信息**：基于子智能体结果的详细内容
        3. **实用信息**：联系方式、预订信息、注意事项等
        4. **质量说明**：信息完整性和可靠性评估

        **输出格式**：
        使用Markdown格式生成结构化的旅行计划报告，确保：
        - 层次清晰，便于阅读
        - 信息完整，逻辑连贯
        - 来源明确，依据充分
        - 实用性强，可操作性好

        请根据所有子智能体的执行结果，生成专业的旅行计划报告。
        """;
        
        return addTimeInfoToSystemPrompt(basePrompt);
    }
    
    /**
     * 构建总结用户提示词
     */
    private String buildSummaryUserPrompt(MainGraphState state) {
        String originalUserQuery = state.getOriginalUserQuery().orElse("用户查询");
        Map<String, String> subgraphResults = state.getSubgraphResults().orElse(Map.of());
        
        // 构建子图结果信息
        String subgraphResultsInfo = buildSubgraphResultsInfo(subgraphResults);
        
        return String.format("""
        **用户原始需求**：%s

        **所有子智能体执行结果**：
        %s

        **报告生成要求**：
        请基于以上所有子智能体的执行结果，生成一份完整的旅行计划报告。

        **报告模板**：
        ```markdown
        # 旅行计划报告

        ## 执行摘要
        - 旅行目的地：[基于子智能体结果]
        - 旅行时间：[基于子智能体结果]
        - 主要安排：[基于子智能体结果]
        - 预算估算：[基于子智能体结果]

        ## 详细信息

        ### 目的地信息
        - 目的地概况：[基于metaSearchAgent结果]
        - 主要景点：[基于metaSearchAgent结果]
        - 当地特色：[基于metaSearchAgent结果]

        ### 行程安排
        - 详细行程：[基于itineraryPlannerAgent结果]
        - 时间安排：[基于itineraryPlannerAgent结果]
        - 交通方式：[基于itineraryPlannerAgent结果]

        ### 预订信息
        - 住宿预订：[基于bookingAgent结果]
        - 交通预订：[基于bookingAgent结果]
        - 门票预订：[基于bookingAgent结果]

        ### 实用信息
        - 联系方式：[基于子智能体结果]
        - 注意事项：[基于子智能体结果]
        - 紧急求助：[基于子智能体结果]

        ## 质量说明
        - 信息完整性：[评估信息完整程度]
        - 可靠性评估：[基于子智能体执行结果]
        - 建议补充：[如有信息不足，说明需要补充的内容]
        ```

        **重要要求**：
        1. **严格基于执行结果**：所有信息必须来源于子智能体的实际执行结果
        2. **禁止编造信息**：不得使用训练数据或常识补充任何信息
        3. **明确标注来源**：每个推荐项目都要说明其依据来源
        4. **信息不足处理**：对于缺失的信息，必须明确标注"信息不足"或"需要进一步收集"
        5. **结构清晰**：使用Markdown格式，层次分明，便于阅读
        6. **实用性强**：确保报告内容实用，可操作性好

        请根据以上要求，生成专业的旅行计划报告。
        """, originalUserQuery, subgraphResultsInfo);
    }
    
    /**
     * 构建子图结果信息
     */
    private String buildSubgraphResultsInfo(Map<String, String> subgraphResults) {
        if (subgraphResults.isEmpty()) {
            return "暂无子智能体执行结果";
        }
        
        StringBuilder info = new StringBuilder();
        subgraphResults.forEach((agent, result) -> 
            info.append(String.format("**%s执行结果**：\n%s\n\n", agent, result))
        );
        return info.toString();
    }
}
