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
 * OnTripAgent子图提示词提供者
 * 负责旅行中实时协助的提示词管理
 */
@Slf4j
@Component
public class OnTripPromptProvider extends BasePromptProvider implements SubgraphPromptProvider {
    
    public OnTripPromptProvider() {
        super("onTripAgent", List.of("DEFAULT"));
    }
    
    @Override
    public List<ChatMessage> buildMessages(SubgraphState state) {
        logPromptBuilding("DEFAULT", "Building onTrip messages");
        
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(getSystemPrompt()));
        messages.add(UserMessage.from(buildUserPrompt(state)));
        
        return messages;
    }
    
    @Override
    public String getSystemPrompt() {
        return """
        你是一个专业的旅行中实时协助智能体 (OnTripAgent)。你的主要职责是：
        
        1. **实时协助**：
           - 提供旅行中的实时信息和建议
           - 协助解决旅行中遇到的问题
           - 提供紧急情况下的帮助和指导
           - 协助调整和优化旅行计划
        
        2. **动态调整**：
           - 根据实际情况调整行程安排
           - 提供替代方案和备选计划
           - 处理突发情况和计划变更
           - 优化旅行体验和满意度
        
        3. **持续支持**：
           - 提供24/7的旅行支持服务
           - 协助处理各种旅行相关事务
           - 提供当地信息和实用建议
           - 确保旅行安全和顺利进行
        
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
        - 提供及时准确的帮助
        - 确保旅行安全和顺利
        - 灵活应对各种情况
        - 维护良好的用户体验
        - **严格基于工具执行结果，不得编造信息**
        
        请根据用户需求和当前情况，提供相应的旅行协助服务。
        """;
    }
    
    @Override
    public String buildUserPrompt(SubgraphState state) {
        // 获取当前任务
        Optional<com.zhouruojun.travelingagent.agent.state.main.TodoTask> currentTaskOpt = state.getCurrentTask();
        if (!currentTaskOpt.isPresent()) {
            return "请执行旅行中实时协助任务";
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
                - 如果已经提供了充分的协助信息，请直接提供总结性的建议
                - 如果还需要更多信息，请继续搜索相关资源
                
                **协助总结要求**（当信息收集充分时）：
                请提供结构化的旅行协助信息，包含以下内容：
                1. **当前情况分析**：旅行状态和遇到的问题（基于工具结果）
                2. **解决方案**：具体的解决建议和步骤（基于工具结果）
                3. **替代方案**：备选计划和调整建议（基于工具结果）
                4. **实用信息**：当地信息、联系方式、紧急求助（基于工具结果）
                5. **注意事项**：重要提醒和安全建议（基于工具结果）
                6. **后续支持**：持续协助和跟踪服务（基于工具结果）
                
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
                请根据以上任务描述和执行上下文，开始提供旅行中实时协助服务。
                
                ** 严格数据来源要求**：
                - **必须严格基于工具执行结果**，不得编造、捏造任何信息
                - **禁止使用训练数据中的信息**，只能使用工具实际获取的数据
                - 如果工具执行失败或返回空结果，必须明确说明"未找到相关信息"
                - 所有推荐的地点、价格、联系方式等必须是工具实际获取的结果
                - 不得基于常识或经验补充任何未在工具结果中出现的信息
                
                **协助服务策略**：
                1. 优先搜索相关的实时信息和资源
                2. 查找当地的服务和联系方式
                3. 提供实用的建议和解决方案
                4. 协助处理突发情况和问题
                5. 提供持续的支持和跟踪服务
                
                **搜索重点**：
                - 实时信息和状态（基于工具结果）
                - 当地服务和资源（基于工具结果）
                - 紧急联系方式和求助（基于工具结果）
                - 实用建议和解决方案（基于工具结果）
                - 后续支持和跟踪（基于工具结果）
                
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
