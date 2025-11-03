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
        super("itineraryPlanner", List.of("DEFAULT"));
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
        String basePrompt = """
        你是一个专业的旅游行程规划智能体 (PlannerAgent)。你的主要职责是：
        
        1. **制定旅游行程方案**：
           - 基于收集到的信息和用户需求，制定详细的旅游行程安排
           - 按天安排具体的行程活动，包括景点游览、用餐、休息等
           - 提供详细的时间安排和地点信息
           - 考虑路线优化，确保时间利用最大化
           - 平衡景点游览、休息时间和交通安排
        
        2. **制定机酒规划方案**：
           - 基于行程安排，推荐合适的机票和酒店
           - 确保机酒时间与行程时间匹配
           - 考虑地理位置便利性
           - 提供价格估算和预订建议
        
        3. **方案输出格式**：
           请按照以下格式输出方案：
           
           **行程规划**：
           [详细的行程安排，按天列出，包含具体时间、地点、活动等]
           
           **机酒规划**：
           [机票和酒店的详细规划，包含时间、地点、建议等]
        
        **严格数据来源要求**：
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
        - 必须同时输出行程规划和机酒规划两部分
        
        **迭代改进**：
        - 如果收到监督反馈，请根据反馈意见修改方案
        - 重点关注时间冲突、逻辑不一致、不合理安排等问题
        - 确保修改后的方案解决了反馈中提到的问题
        
        工作原则：
        - 基于实际数据制定计划
        - 考虑用户的个性化需求
        - 确保方案的逻辑一致性和时间合理性
        - 提供实用可操作的方案
        - **严格基于工具执行结果，不得编造信息**
        
        请根据用户需求和收集到的信息，制定详细的旅游行程和机酒方案。
        """;
        
        return addTimeInfoToSystemPrompt(basePrompt);
    }
    
    @Override
    public String buildUserPrompt(SubgraphState state) {
        // 获取当前任务
        Optional<com.zhouruojun.travelingagent.agent.state.main.TodoTask> currentTaskOpt = state.getCurrentTask();
        String taskDescription = currentTaskOpt.isPresent() 
            ? currentTaskOpt.get().getDescription() 
            : "请执行旅行计划优化任务";
        
        String executionContext = state.getSubgraphContext().orElse(null);
        String toolExecutionResult = state.getToolExecutionResult().orElse(null);
        
        // 检查是否是迭代改进（有监督反馈）
        boolean isIteration = state.getSupervisorFeedback().isPresent();
        int iterationCount = state.getIterationCount();
        
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
        
        // 如果是迭代改进，添加监督反馈
        if (isIteration) {
            userPrompt.append("**当前迭代轮次**: 第 ").append(iterationCount + 1).append(" 轮\n\n");
            userPrompt.append("=== 上一轮监督反馈 ===\n");
            state.getSupervisorFeedback().ifPresent(feedback -> {
                userPrompt.append(feedback).append("\n\n");
            });
            userPrompt.append("请根据上述监督反馈，修改并改进您的方案，解决反馈中提到的问题。\n\n");
            
            // 显示当前的方案（如果需要修改）
            state.getCurrentPlan().ifPresent(plan -> {
                userPrompt.append("**当前完整方案**:\n");
                userPrompt.append(plan).append("\n\n");
            });
        }
        
        // 如果有工具执行结果，添加到提示词中
        if (hasToolExecutionResult) {
            userPrompt.append("**工具执行结果**:\n");
            userPrompt.append(toolExecutionResult).append("\n\n");
        }
        
        // 根据场景添加不同的指导
        if (isIteration) {
            // 迭代改进场景
            userPrompt.append("""
                **修改要求**：
                1. 仔细阅读监督反馈，理解需要修改的具体问题
                2. 重点关注：时间冲突、逻辑不一致、不合理安排等问题
                3. 修改方案时确保解决所有反馈中提到的问题
                4. 修改后的方案必须保持完整性和可操作性
                5. 必须同时输出修改后的行程规划和机酒规划
                
                **输出格式**（必须包含两部分）：
                
                **行程规划**：
                [修改后的详细行程安排]
                
                **机酒规划**：
                [修改后的机票和酒店规划]
                
                **严格数据来源要求**：
                - **必须严格基于工具执行结果**，不得编造、捏造任何信息
                - 如果工具执行失败或返回空结果，必须明确说明"未找到相关信息"
                - 所有信息必须是工具实际获取的结果
                
                请根据监督反馈修改方案，确保方案的正确性和合理性。
                """);
        } else if (hasToolExecutionResult) {
            // 首次制定方案且有工具结果
            userPrompt.append("""
                基于以上工具执行结果、任务描述和执行上下文，请制定完整的旅游行程和机酒方案。
                
                ** 严格数据来源要求**：
                - **必须严格基于工具执行结果**，不得编造、捏造任何信息
                - **禁止使用训练数据中的信息**，只能使用工具实际获取的数据
                - 如果工具执行失败或返回空结果，必须明确说明"未找到相关信息"
                - 所有推荐的地点、价格、联系方式等必须是工具实际获取的结果
                - 不得基于常识或经验补充任何未在工具结果中出现的信息
                
                **方案制定要求**：
                请提供完整的方案，必须包含两部分：
                
                1. **行程规划**（必须包含）：
                   - 按天安排的具体行程
                   - 详细的时间安排和地点信息
                   - 景点游览、用餐、休息等安排
                   - 确保时间安排合理，无冲突
                
                2. **机酒规划**（必须包含）：
                   - 机票推荐和预订建议
                   - 酒店推荐和预订建议
                   - 确保机酒时间与行程时间匹配
                   - 考虑地理位置便利性
                
                **输出格式**（必须严格按照此格式）：
                
                **行程规划**：
                [详细的行程安排]
                
                **机酒规划**：
                [机票和酒店的详细规划]
                
                **输出要求**：
                - 所有信息必须来源于工具执行结果
                - 如果工具执行失败，必须明确说明"工具执行失败，无法获取信息"
                - 对于工具未提供的信息，必须明确标注"需要进一步搜索"或"信息不足"
                - 不得为了完整性而编造任何细节
                - 必须同时输出行程规划和机酒规划两部分
                
                **重要**：避免重复使用相同的搜索条件！如果已经搜索过，请直接分析现有结果并制定方案。
                """);
        } else {
            // 首次执行，需要先收集信息
            userPrompt.append("""
                请根据以上任务描述和执行上下文，开始收集信息并制定详细的旅行计划。
                
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
