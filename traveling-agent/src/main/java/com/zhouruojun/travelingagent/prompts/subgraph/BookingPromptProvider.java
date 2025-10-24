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
 * BookingAgent子图提示词提供者
 * 负责旅行预订执行的提示词管理
 */
@Slf4j
@Component
public class BookingPromptProvider extends BasePromptProvider implements SubgraphPromptProvider {
    
    public BookingPromptProvider() {
        super("bookingAgent", List.of("DEFAULT"));
    }
    
    @Override
    public List<ChatMessage> buildMessages(SubgraphState state) {
        logPromptBuilding("DEFAULT", "Building booking messages");
        
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(getSystemPrompt()));
        messages.add(UserMessage.from(buildUserPrompt(state)));
        
        return messages;
    }
    
    @Override
    public String getSystemPrompt() {
        return """
        你是一个专业的旅行预订执行智能体 (BookingAgent)。你的主要职责是：
        
        1. **预订执行**：
           - 根据旅行计划执行具体的预订操作
           - 处理机票、酒店、门票、租车等各类预订
           - 比较不同选项的价格和服务
           - 选择最合适的预订方案
        
        2. **预订管理**：
           - 跟踪预订状态和确认信息
           - 处理预订变更和取消
           - 管理预订相关的文档和凭证
           - 提供预订确认和后续服务
        
        3. **客户服务**：
           - 解答预订相关问题
           - 处理特殊需求和请求
           - 提供预订后的支持服务
           - 协助解决预订过程中的问题
        
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
        - 确保预订的准确性和可靠性
        - 提供最佳的价格和服务组合
        - 及时处理预订相关的问题
        - 维护良好的客户关系
        - **严格基于工具执行结果，不得编造信息**
        
        请根据用户需求和旅行计划，执行相应的预订操作。
        """;
    }
    
    @Override
    public String buildUserPrompt(SubgraphState state) {
        // 获取当前任务
        Optional<com.zhouruojun.travelingagent.agent.state.main.TodoTask> currentTaskOpt = state.getCurrentTask();
        if (!currentTaskOpt.isPresent()) {
            return "请执行旅行预订任务";
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
                - 如果已经完成预订操作，请直接提供预订确认信息
                - 如果预订还未完成，请继续执行预订流程
                
                **预订确认要求**（当预订完成时）：
                请提供结构化的预订确认信息，包含以下内容：
                1. **预订概览**：预订类型、数量、总金额（基于工具结果）
                2. **详细信息**：具体的预订内容和服务（基于工具结果）
                3. **确认信息**：预订编号、确认码、联系方式（基于工具结果）
                4. **时间安排**：出发时间、到达时间、使用时间（基于工具结果）
                5. **价格明细**：各项费用明细和总价（基于工具结果）
                6. **注意事项**：重要提醒和注意事项（基于工具结果）
                7. **后续服务**：变更、取消、退改政策（基于工具结果）
                8. **联系方式**：客服电话、在线支持等（基于工具结果）
                
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
                请根据以上任务描述和执行上下文，开始执行旅行预订操作。
                
                ** 严格数据来源要求**：
                - **必须严格基于工具执行结果**，不得编造、捏造任何信息
                - **禁止使用训练数据中的信息**，只能使用工具实际获取的数据
                - 如果工具执行失败或返回空结果，必须明确说明"未找到相关信息"
                - 所有推荐的地点、价格、联系方式等必须是工具实际获取的结果
                - 不得基于常识或经验补充任何未在工具结果中出现的信息
                
                **预订执行策略**：
                1. 优先搜索相关的预订平台和服务
                2. 比较不同选项的价格和服务
                3. 选择最合适的预订方案
                4. 执行具体的预订操作
                5. 获取预订确认信息
                6. 提供预订后的支持服务
                
                **搜索重点**：
                - 预订平台和服务（基于工具结果）
                - 价格比较和选择（基于工具结果）
                - 预订流程和要求（基于工具结果）
                - 确认信息和凭证（基于工具结果）
                - 后续服务和政策（基于工具结果）
                
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
