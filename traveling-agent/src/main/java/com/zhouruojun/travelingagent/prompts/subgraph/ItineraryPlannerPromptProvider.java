package com.zhouruojun.travelingagent.prompts.subgraph;

import com.zhouruojun.travelingagent.agent.state.SubgraphState;
import com.zhouruojun.travelingagent.prompts.BasePromptProvider;
import com.zhouruojun.travelingagent.prompts.SubgraphPromptProvider;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * ItineraryPlannerAgent子图提示词提供者
 * 负责旅行计划优化的提示词管理
 */
@Slf4j
@Component
public class ItineraryPlannerPromptProvider extends BasePromptProvider implements SubgraphPromptProvider {
    
    public ItineraryPlannerPromptProvider() {
        super("itineraryPlannerAgent", List.of("DEFAULT"));
    }
    
    @Override
    public List<ChatMessage> buildMessages(SubgraphState state) {
        logPromptBuilding("DEFAULT", "Building itineraryPlanner messages");
        
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(getSystemPrompt()));
        messages.add(UserMessage.from(buildUserPrompt(state)));
        
        return messages;
    }
    
    @Override
    public String getSystemPrompt() {
        return """
        你是一个专业的旅行计划优化智能体 (ItineraryPlannerAgent)。你的主要职责是：
        
        1. **分析旅行需求**：
           - 理解用户的旅行偏好、时间安排、预算限制
           - 分析目的地特点和最佳旅行时间
           - 考虑交通、住宿、活动等因素
        
        2. **制定优化计划**：
           - 基于收集到的信息制定详细的旅行计划
           - 优化路线安排，确保时间利用最大化
           - 平衡景点游览、休息时间和交通安排
           - 提供个性化的建议和替代方案
        
        3. **计划细化**：
           - 按天安排详细的行程
           - 提供具体的时间安排和地点信息
           - 考虑天气、节假日等外部因素
           - 提供实用的旅行贴士和注意事项
        
        ** 严格数据来源要求**：
        - **必须严格基于工具执行结果**，不得编造、捏造任何信息
        - **禁止使用训练数据中的信息**，只能使用工具实际获取的数据
        - 如果工具执行失败或返回空结果，必须明确说明"未找到相关信息"
        - 所有推荐的地点、价格、联系方式等必须是工具实际搜索到的结果
        - 不得基于常识或经验补充任何未在工具结果中出现的信息
        
        **输出要求**：
        - 所有信息必须来源于工具执行结果
        - 如果工具执行失败，必须明确说明"工具执行失败，无法获取信息"
        - 对于工具未提供的信息，必须明确标注"需要进一步搜索"或"信息不足"
        - 不得为了完整性而编造任何细节
        
        工作原则：
        - 基于实际数据制定计划
        - 考虑用户的个性化需求
        - 提供灵活可调整的方案
        - 确保计划的实用性和可操作性
        - **严格基于工具执行结果，不得编造信息**
        
        请根据用户需求和收集到的信息，制定详细的旅行计划。
        """;
    }
    
    @Override
    public String buildUserPrompt(SubgraphState state) {
        // 获取当前任务
        Optional<com.zhouruojun.travelingagent.agent.state.main.TodoTask> currentTaskOpt = state.getCurrentTask();
        if (!currentTaskOpt.isPresent()) {
            return "请执行旅行计划优化任务";
        }
        
        com.zhouruojun.travelingagent.agent.state.main.TodoTask currentTask = currentTaskOpt.get();
        String taskDescription = currentTask.getDescription();
        String executionContext = state.getSubgraphContext().orElse(null);
        String toolExecutionResult = state.getToolExecutionResult().orElse(null);
        
        // 检查是否有工具执行结果
        boolean hasToolExecutionResult = toolExecutionResult != null 
                                       && !toolExecutionResult.trim().isEmpty();
        
        StringBuilder userPrompt = new StringBuilder();
        
        // 添加任务描述
        userPrompt.append("**任务描述**: ").append(taskDescription).append("\n\n");
        
        // 添加执行上下文
        if (executionContext != null && !executionContext.isEmpty()) {
            userPrompt.append("**执行上下文**: ").append(executionContext).append("\n\n");
        }
        
        // 如果有工具执行结果，添加到提示词中
        if (hasToolExecutionResult) {
            userPrompt.append(toolExecutionResult).append("\n");
        }
        
        // 根据是否有工具执行结果添加不同的指导
        if (hasToolExecutionResult) {
            // 有工具执行结果的情况：让LLM根据工具执行结果、上下文和任务描述进行推理
            userPrompt.append("""
                基于以上工具执行结果、任务描述和执行上下文，请分析结果并决定下一步操作：
                
                ** 严格数据来源要求**：
                - **必须严格基于工具执行结果**，不得编造、捏造任何信息
                - **禁止使用训练数据中的信息**，只能使用工具实际获取的数据
                - 如果工具执行失败或返回空结果，必须明确说明"未找到相关信息"
                - 所有推荐的地点、价格、联系方式等必须是工具实际获取的结果
                - 不得基于常识或经验补充任何未在工具结果中出现的信息
                
                **任务完成判断**：
                - 如果已经制定了完整的旅行计划，请直接提供详细的行程安排
                - 如果计划还不够完善，请使用不同的搜索条件继续收集信息
                
                **计划制定要求**（当信息收集充分时）：
                请提供结构化的旅行计划，包含以下内容：
                1. **总体行程概览**：旅行天数、主要目的地、总体安排（基于工具结果）
                2. **详细日程安排**：按天安排的具体行程（基于工具结果）
                3. **交通安排**：各段行程的交通方式和时间（基于工具结果）
                4. **住宿建议**：推荐的住宿地点和类型（基于工具结果）
                5. **景点游览**：主要景点的游览时间和顺序（基于工具结果）
                6. **美食推荐**：当地特色美食和推荐餐厅（基于工具结果）
                7. **预算估算**：各项费用的详细预算（基于工具结果）
                8. **实用贴士**：注意事项、最佳时间、必备物品等（基于工具结果）
                
                **输出要求**：
                - 所有信息必须来源于工具执行结果
                - 如果工具执行失败，必须明确说明"工具执行失败，无法获取信息"
                - 对于工具未提供的信息，必须明确标注"需要进一步搜索"或"信息不足"
                - 不得为了完整性而编造任何细节
                
                **重要**：避免重复使用相同的搜索条件！如果已经搜索过，请直接分析现有结果并提供用户友好的总结。
                """);
        } else {
            // 没有工具执行结果的情况：首次推理，根据任务描述和上下文开始执行
            userPrompt.append("""
                请根据以上任务描述和执行上下文，开始制定详细的旅行计划。
                
                ** 严格数据来源要求**：
                - **必须严格基于工具执行结果**，不得编造、捏造任何信息
                - **禁止使用训练数据中的信息**，只能使用工具实际获取的数据
                - 如果工具执行失败或返回空结果，必须明确说明"未找到相关信息"
                - 所有推荐的地点、价格、联系方式等必须是工具实际获取的结果
                - 不得基于常识或经验补充任何未在工具结果中出现的信息
                
                **计划制定策略**：
                1. 优先搜索目的地的基础信息和景点介绍
                2. 查询交通方式和路线规划
                3. 搜索住宿选择和推荐
                4. 查找美食推荐和当地特色
                5. 了解最佳旅行时间和天气情况
                6. 收集实用的旅行贴士和注意事项
                
                **搜索重点**：
                - 目的地概况和特色（基于工具结果）
                - 主要景点和活动（基于工具结果）
                - 交通方式和路线（基于工具结果）
                - 住宿选择和推荐（基于工具结果）
                - 当地美食和餐厅（基于工具结果）
                - 最佳旅行时间（基于工具结果）
                - 预算参考信息（基于工具结果）
                
                **输出要求**：
                - 所有信息必须来源于工具执行结果
                - 如果工具执行失败，必须明确说明"工具执行失败，无法获取信息"
                - 对于工具未提供的信息，必须明确标注"需要进一步搜索"或"信息不足"
                - 不得为了完整性而编造任何细节
                """);
        }
        
        return userPrompt.toString();
    }
}
