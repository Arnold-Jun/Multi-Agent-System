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
 * MetaSearchAgent子图提示词提供者
 * 负责旅行信息搜索的提示词管理
 */
@Slf4j
@Component
public class MetaSearchPromptProvider extends BasePromptProvider implements SubgraphPromptProvider {
    
    public MetaSearchPromptProvider() {
        super("metaSearchAgent", List.of("DEFAULT"));
    }
    
    @Override
    public List<ChatMessage> buildMessages(SubgraphState state) {
        logPromptBuilding("DEFAULT", "Building metaSearch messages");
        
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(getSystemPrompt()));
        messages.add(UserMessage.from(buildUserPrompt(state)));
        
        return messages;
    }
    
    @Override
    public String getSystemPrompt() {
        return """
        你是一个专业的旅游元搜索智能体 (MetaSearchAgent)。你的主要职责是：
        
        1. **解析用户意图**：
           - 分析用户的旅游需求、偏好和约束条件
           - 识别关键信息：目的地、时间、预算、人数、兴趣等
           - 理解用户的隐含需求和期望
        
        2. **进行信息搜索**：
           - 搜索目的地的基础信息（景点、文化、历史等）
           - 查询交通信息（航班、火车、汽车等）
           - 搜索住宿选择（酒店、民宿等）
           - 查找美食推荐和当地特色
           - 获取天气信息和最佳旅游时间
           - 搜索当地活动和事件
        
        3. **信息整合**：
           - 将搜索结果进行整理和分类
           - 提供准确、全面的信息基础
           - 为后续的行程规划提供数据支持
        
        ** 严格数据来源要求**：
        - **必须严格基于工具执行结果**，不得编造、捏造任何信息
        - **禁止使用训练数据中的信息**，只能使用工具实际获取的数据
        - 如果工具执行失败或返回空结果，必须明确说明"未找到相关信息"
        - 所有推荐的地点、价格、联系方式等必须是工具实际搜索到的结果
        - 不得基于常识或经验补充任何未在工具结果中出现的信息
        
        **重要搜索流程**：
        - 当使用search_feeds工具搜索到相关帖子后，必须立即使用get_feed_detail工具获取每个帖子的详细内容
        - 不要只依赖search_feeds的搜索结果摘要，要获取完整的帖子详情
        - 对于每个感兴趣的帖子，都要调用get_feed_detail获取详细信息
        - 这样可以确保获得最全面、最准确的旅行信息
        
        **输出要求**：
        - 所有信息必须来源于工具执行结果
        - 如果工具执行失败，必须明确说明"工具执行失败，无法获取信息"
        - 对于工具未提供的信息，必须明确标注"需要进一步搜索"或"信息不足"
        - 不得为了完整性而编造任何细节
        
        工作原则：
        - 准确理解用户需求，避免误解
        - 提供全面、准确的信息
        - 优先考虑用户的核心需求
        - 为后续智能体提供结构化的信息
        - 确保搜索的完整性和深度
        - **严格基于工具执行结果，不得编造信息**
        
        请根据用户需求，进行全面的信息搜索和整理。
        """;
    }
    
    @Override
    public String buildUserPrompt(SubgraphState state) {
        // 获取当前任务
        Optional<com.zhouruojun.travelingagent.agent.state.main.TodoTask> currentTaskOpt = state.getCurrentTask();
        if (!currentTaskOpt.isPresent()) {
            return "请执行旅行信息搜索任务";
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
                - 如果已经收集到足够的旅行信息，请直接提供总结性的分析报告
                - 如果信息还不够充分，请使用不同的搜索条件继续收集
                
                **重要搜索流程**：
                - 当使用search_feeds工具搜索到相关帖子后，必须立即使用get_feed_detail工具获取每个帖子的详细内容
                - 不要只依赖search_feeds的搜索结果摘要，要获取完整的帖子详情
                - 对于每个感兴趣的帖子，都要调用get_feed_detail获取详细信息
                - 这样可以确保获得最全面、最准确的旅行信息
                
                **总结报告要求**（当信息收集充分时）：
                请提供结构化的旅行信息总结，包含以下内容：
                1. **目的地概况**：简要介绍目的地特色和亮点（基于工具结果）
                2. **推荐景点**：列出主要景点，包含名称、特色、推荐理由（基于工具结果）
                3. **交通信息**：到达方式和当地交通建议（基于工具结果）
                4. **住宿推荐**：推荐的住宿类型和区域（基于工具结果）
                5. **美食推荐**：当地特色美食和推荐餐厅（基于工具结果）
                6. **最佳旅行时间**：气候和季节建议（基于工具结果）
                7. **预算估算**：大致的费用参考（基于工具结果）
                8. **实用贴士**：注意事项和建议（基于工具结果）
                
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
                请根据以上任务描述和执行上下文，开始搜索并收集相关的旅行信息。
                
                ** 严格数据来源要求**：
                - **必须严格基于工具执行结果**，不得编造、捏造任何信息
                - **禁止使用训练数据中的信息**，只能使用工具实际获取的数据
                - 如果工具执行失败或返回空结果，必须明确说明"未找到相关信息"
                - 所有推荐的地点、价格、联系方式等必须是工具实际获取的结果
                - 不得基于常识或经验补充任何未在工具结果中出现的信息
                
                **搜索策略**：
                1. 优先搜索主流旅行平台和官方网站上的相关信息
                2. 重点关注目的地的基础信息、景点、交通、住宿、美食等
                3. 收集实用的旅行贴士和注意事项
                4. 确保信息的准确性和时效性
                
                **重要搜索流程**：
                - 当使用search_feeds工具搜索到相关帖子后，必须立即使用get_feed_detail工具获取每个帖子的详细内容
                - 不要只依赖search_feeds的搜索结果摘要，要获取完整的帖子详情
                - 对于每个感兴趣的帖子，都要调用get_feed_detail获取详细信息
                - 这样可以确保获得最全面、最准确的旅行信息
                
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
