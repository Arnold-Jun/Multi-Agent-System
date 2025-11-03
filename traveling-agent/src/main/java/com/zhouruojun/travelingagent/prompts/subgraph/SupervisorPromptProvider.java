package com.zhouruojun.travelingagent.prompts.subgraph;

import com.zhouruojun.travelingagent.agent.state.SubgraphState;
import com.zhouruojun.travelingagent.prompts.BasePromptProvider;
import com.zhouruojun.travelingagent.prompts.SubgraphPromptProvider;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * SupervisorAgent子图提示词提供者
 * 负责监督审查plannerAgent生成的方案
 */
@Slf4j
@Component
public class SupervisorPromptProvider extends BasePromptProvider implements SubgraphPromptProvider {
    
    public SupervisorPromptProvider() {
        super("itinerarySupervisor", List.of("DEFAULT"));
    }
    
    @Override
    public List<ChatMessage> buildMessages(SubgraphState state) {
        logPromptBuilding("DEFAULT", "Building itinerarySupervisor messages");
        
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(getSystemPrompt()));
        messages.add(UserMessage.from(buildUserPrompt(state)));
        
        return messages;
    }
    
    @Override
    public String getSystemPrompt() {
        String basePrompt = """
        你是一个专业的旅游方案监督审查智能体 (SupervisorAgent)。你的主要职责是：
        
        1. **审查方案完整性**：
           - 检查行程方案是否完整，包含所有必要的信息
           - 检查机酒方案是否完整，包含机票和酒店的详细信息
           - 确保方案没有遗漏关键环节
        
        2. **检查时间一致性**：
           - 仔细检查行程中的时间安排是否存在冲突
           - 验证各个活动之间的时间是否合理衔接
           - 确保机酒时间与行程时间匹配
           - 检查是否存在时间重叠或无法实现的时间安排
        
        3. **检查逻辑一致性**：
           - 验证行程路线是否合理（地理位置、交通距离）
           - 检查活动安排是否符合逻辑顺序
           - 确保机酒方案与行程方案在地点和时间上一致
           - 检查预算和时间是否匹配
        
        4. **检查合理性**：
           - 评估方案的可行性和可操作性
           - 检查是否存在不合理的安排（如时间过紧、路线不合理等）
           - 验证方案是否符合用户的需求和约束条件
           - 评估整体方案的实用性和体验质量
        
        5. **提供审查反馈**：
           - 如果发现没有问题，明确说明"方案通过，没有问题"
           - 如果发现问题，详细说明具体的问题点
           - 提供具体的修改建议和改进方向
        
        **审查原则**：
        - 严格审查，不放过任何潜在问题
        - 客观公正，基于事实和逻辑进行判断
        - 提供清晰明确的反馈，便于后续修改
        
        **输出格式要求（严格JSON格式）**：
        你必须输出严格的JSON格式，包含以下字段：
        
        ```json
        {
          "next": "Finish" 或 "revise",
          "output": "审查结果或修改意见"
        }
        ```
        
        **next字段说明**：
        - "Finish": 表示审查通过，方案合格，结束子图
        - "revise": 表示需要修改，返回itineraryPlanner进行修改
        
        **output字段说明**：
        - 如果next是"Finish"：可以为空字符串，或包含"方案审查通过"等信息
        - 如果next是"revise"：必须包含具体的修改意见，详细说明：
          * 发现的具体问题（时间冲突、逻辑不一致、不合理等）
          * 问题出现的位置和原因
          * 具体的修改建议和改进方向
        
        **重要**：
        - 只输出JSON，不要其他任何内容
        - 确保JSON格式正确，字段名称必须是小写：next, output
        - 如果发现问题，next必须设置为"revise"，并在output中提供详细的修改意见
        - 如果方案完全正确，next设置为"Finish"
        
        请仔细审查提供的方案，输出JSON格式的审查结论。
        """;
        
        return addTimeInfoToSystemPrompt(basePrompt);
    }
    
    @Override
    public String buildUserPrompt(SubgraphState state) {
        StringBuilder userPrompt = new StringBuilder();
        
        // 获取当前任务
        Optional<com.zhouruojun.travelingagent.agent.state.main.TodoTask> currentTaskOpt = state.getCurrentTask();
        if (currentTaskOpt.isPresent()) {
            userPrompt.append("**任务描述**: ").append(currentTaskOpt.get().getDescription()).append("\n\n");
        }
        
        // 获取执行上下文
        state.getSubgraphContext().ifPresent(context -> {
            userPrompt.append("**执行上下文**: ").append(context).append("\n\n");
        });
        
        // 获取当前迭代次数
        int iterationCount = state.getIterationCount();
        userPrompt.append("**当前迭代次数**: ").append(iterationCount).append(" (最大5次)\n\n");
        
        // 添加完整方案（从currentPlan或messages中获取）
        Optional<String> currentPlan = state.getCurrentPlan();
        if (currentPlan.isPresent()) {
            userPrompt.append("=== 完整旅游规划方案 ===\n");
            userPrompt.append(currentPlan.get()).append("\n\n");
        } else {
            // 如果没有currentPlan，尝试从最后一条消息中获取（来自Planner的响应）
            Optional<ChatMessage> lastMessage = state.lastMessage();
            if (lastMessage.isPresent() && lastMessage.get() instanceof AiMessage) {
                String planText = ((AiMessage) lastMessage.get()).text();
                if (planText != null && !planText.trim().isEmpty()) {
                    userPrompt.append("=== 完整旅游规划方案 ===\n");
                    userPrompt.append(planText).append("\n\n");
                } else {
                    userPrompt.append("=== 完整旅游规划方案 ===\n");
                    userPrompt.append("（暂无方案）\n\n");
                }
            } else {
                userPrompt.append("=== 完整旅游规划方案 ===\n");
                userPrompt.append("（暂无方案）\n\n");
            }
        }
        
        // 如果有之前的监督反馈，添加进去（用于多轮迭代）
        Optional<String> previousFeedback = state.getSupervisorFeedback();
        if (previousFeedback.isPresent() && iterationCount > 1) {
            userPrompt.append("=== 上一轮审查反馈 ===\n");
            userPrompt.append(previousFeedback.get()).append("\n\n");
        }
        
        // 添加审查要求
        userPrompt.append("""
            === 审查要求 ===
            
            请仔细审查上述方案，重点检查以下方面：
            
            1. **时间冲突检查**：
               - 行程中的活动时间是否存在重叠
               - 机酒时间与行程时间是否匹配
               - 交通时间是否充足合理
            
            2. **逻辑一致性检查**：
               - 行程路线是否合理
               - 活动顺序是否符合逻辑
               - 机酒方案与行程方案是否一致
            
            3. **合理性检查**：
               - 方案是否可行和可操作
               - 是否存在不合理的安排
               - 是否符合用户需求
            
            4. **完整性检查**：
               - 方案是否包含所有必要信息
               - 是否有遗漏的关键环节
            
            **输出要求**：
            - 如果方案完全正确，请明确说"方案审查通过，没有问题"
            - 如果发现问题，请详细说明：
              * 发现的具体问题（时间冲突、不一致、不合理等）
              * 问题出现的位置和原因
              * 具体的修改建议
            
            请给出明确的审查结论和反馈。
            """);
        
        return userPrompt.toString();
    }
}
