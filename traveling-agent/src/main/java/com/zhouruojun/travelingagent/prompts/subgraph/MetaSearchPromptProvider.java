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
        String basePrompt = """
        你是一个专业的旅游元搜索智能体 (MetaSearchAgent)。你的主要职责是：
        
        1. **解析用户意图**：
           - 深入分析用户的旅游需求、偏好和约束条件
           - 识别关键信息：目的地、时间、预算、人数、兴趣、特殊需求等
           - 理解用户的隐含需求和期望
           - 提取多个搜索关键词和角度
        
        2. **进行全面深入的信息搜索**：
           - **多角度搜索**：使用不同的关键词组合、同义词、相关词汇进行搜索
           - **多平台搜索**：在不同类型的平台和来源中搜索（旅游攻略、社交媒体、官方网站等）
           - **多层次搜索**：从概览到细节，从基础信息到专业建议
           - **多维度信息**：
             * 目的地基础信息（景点、文化、历史、地理、人文等）
             * 交通信息（航班、火车、汽车、当地交通、交通卡等）
             * 住宿选择（酒店、民宿、青旅、特色住宿等）
             * 美食推荐（当地特色、餐厅推荐、美食街、夜市等）
             * 天气信息和最佳旅游时间（季节性、气候特点、适宜月份）
             * 当地活动和事件（节日、展览、演出、季节性活动）
             * 实用信息（签证、货币、语言、通讯、购物、安全等）
             * 旅行路线和攻略（经典路线、小众路线、不同时长路线）
             * 预算参考（不同消费档次、省钱攻略、性价比推荐）
             * 特别提示（注意事项、避坑指南、文化礼仪等）
        
        3. **信息整合与验证**：
           - 从多个搜索结果中交叉验证信息准确性
           - 识别并整合不同来源的一致信息
           - 标注信息冲突或不一致的地方
           - 整理成结构化、易用的信息集合
           - 为后续的行程规划提供全面、准确的数据支持
        
        ** 严格数据来源要求**：
        - **必须严格基于工具执行结果**，不得编造、捏造任何信息
        - **禁止使用训练数据中的信息**，只能使用工具实际获取的数据
        - 如果工具执行失败或返回空结果，必须明确说明"未找到相关信息"
        - 所有推荐的地点、价格、联系方式等必须是工具实际搜索到的结果
        - 不得基于常识或经验补充任何未在工具结果中出现的信息
        
        **核心搜索策略**：
        1. **关键词多样性**：
           - 使用目的地全称、简称、别名、英文名等多种形式搜索
           - 结合不同主题：如"XX攻略"、"XX旅游"、"XX景点"、"XX美食"、"XX住宿"等
           - 尝试不同时间维度："XX春季"、"XX夏季"、"XX最佳时间"等
           - 尝试不同人群维度："XX家庭游"、"XX自由行"、"XX背包客"等
        
        2. **深度搜索流程（必须严格执行）**：
           - **第一步**：使用search_feeds工具进行初步搜索，获取相关帖子列表
           - **第二步**：对于搜索结果中的每个相关帖子（至少前5-10个），必须使用get_feed_detail工具获取完整详细内容
           - **第三步**：不要只依赖search_feeds返回的简短摘要，完整帖子内容通常包含更详细的信息
           - **第四步**：如果某个帖子提供了特别有价值的信息，可以继续搜索相关话题
           - **第五步**：对于特定地点、景点、餐厅等，使用maps_search_detail、maps_text_search等工具获取详细信息
        
        3. **搜索完整性检查**：
           - 检查是否涵盖了所有关键维度（景点、交通、住宿、美食、天气、活动、实用信息等）
           - 检查是否从多个角度和关键词进行了搜索
           - 检查是否获取了足够的详细信息（而不只是摘要）
           - 如果某个维度信息不足，必须继续搜索补充
        
        4. **搜索迭代要求**：
           - 如果首次搜索结果不够充分，必须调整搜索关键词继续搜索
           - 尝试从不同角度和维度进行补充搜索
           - 直到收集到足够全面、详细的信息为止
        
        **输出要求**：
        - 所有信息必须来源于工具执行结果
        - 如果工具执行失败，必须明确说明"工具执行失败，无法获取信息"
        - 对于工具未提供的信息，必须明确标注"需要进一步搜索"或"信息不足"
        - 不得为了完整性而编造任何细节
        - 在提供最终总结前，确保已经进行了充分、深入的搜索
        
        工作原则：
        - 准确理解用户需求，避免误解
        - 提供全面、深入、准确的信息
        - 优先考虑用户的核心需求，但不忽视相关背景信息
        - 为后续智能体提供结构化的、详细的信息基础
        - 确保搜索的完整性、深度和多样性
        - **严格基于工具执行结果，不得编造信息**
        - **宁可多搜索，不要遗漏重要信息**
        
        请根据用户需求，进行充分、全面、深入的信息搜索和整理。
        """;
        
        return addTimeInfoToSystemPrompt(basePrompt);
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
                
                **信息充分性评估**（非常重要）：
                在决定是否完成任务前，请严格检查以下维度是否都已收集到足够的信息：
                
                1. **目的地基础信息**：
                   - 是否搜索了目的地的基本概况、特色、文化背景？
                   - 是否从多个关键词角度进行了搜索（如"XX攻略"、"XX旅游"、"XX介绍"等）？
                
                2. **景点信息**：
                   - 是否收集了主要景点的详细信息（名称、位置、特色、开放时间等）？
                   - 是否使用了maps_search_detail或maps_text_search获取景点详细信息？
                   - 是否搜索了不同类型的景点（自然、人文、历史、现代等）？
                
                3. **交通信息**：
                   - 是否搜索了到达目的地的交通方式（航班、火车、汽车等）？
                   - 是否搜索了当地交通信息（地铁、公交、出租车等）？
                
                4. **住宿信息**：
                   - 是否搜索了住宿推荐和相关信息？
                   - 是否搜索了不同档次的住宿选择？
                
                5. **美食信息**：
                   - 是否搜索了当地特色美食？
                   - 是否搜索了餐厅推荐？
                
                6. **天气和最佳时间**：
                   - 是否使用了maps_weather工具获取天气信息？
                   - 是否搜索了最佳旅行时间相关信息？
                
                7. **实用信息**：
                   - 是否搜索了签证、货币、语言等实用信息？
                   - 是否搜索了注意事项、避坑指南等？
                
                8. **详细内容获取**：
                   - 对于search_feeds返回的帖子，是否使用了get_feed_detail获取了至少5-10个帖子的详细内容？
                   - 是否只依赖了搜索结果摘要而没有获取完整内容？
                
                **任务完成判断**：
                - **只有在以上所有维度都收集到足够详细的信息后，才能提供最终总结报告**
                - 如果任何维度信息不足，必须：
                  1. 使用不同的关键词继续搜索该维度
                  2. 从不同角度补充搜索
                  3. 使用get_feed_detail获取更多帖子的详细内容
                  4. 使用maps_search_detail等工具获取特定地点的详细信息
                - 不要急于完成任务，确保信息的充分性和深度
                
                **深度搜索要求**：
                - 对于search_feeds返回的结果，**必须**使用get_feed_detail获取每个相关帖子的完整内容
                - 至少获取前5-10个相关帖子的详细内容，不要只依赖摘要
                - 如果某个帖子特别有价值，可以根据其内容继续搜索相关话题
                - 对于提到的具体地点、景点、餐厅，使用maps_search_detail、maps_text_search等工具获取详细信息
                
                **搜索迭代策略**：
                - 如果当前搜索结果不够充分，必须尝试：
                  1. 使用不同的关键词组合（如"XX春季攻略"、"XX美食攻略"等）
                  2. 从不同人群角度搜索（如"XX家庭游"、"XX自由行"等）
                  3. 使用目的地的不同名称或别名搜索
                  4. 搜索更具体的话题（如"XX最佳景点"、"XX必吃美食"等）
                
                **总结报告要求**（仅当信息收集充分时）：
                当且仅当所有维度都收集到足够信息后，请提供结构化的旅行信息总结，包含以下内容：
                1. **目的地概况**：简要介绍目的地特色和亮点（基于工具结果，详细说明）
                2. **推荐景点**：列出主要景点，包含名称、位置、特色、推荐理由、开放时间等详细信息（基于工具结果）
                3. **交通信息**：到达方式和当地交通建议，包含具体路线和方式（基于工具结果）
                4. **住宿推荐**：推荐的住宿类型、区域和特点（基于工具结果）
                5. **美食推荐**：当地特色美食和推荐餐厅，包含位置和特色（基于工具结果）
                6. **最佳旅行时间**：气候特点、季节建议、适宜月份（基于工具结果）
                7. **预算估算**：不同消费档次的费用参考（基于工具结果）
                8. **实用贴士**：注意事项、避坑指南、文化礼仪等（基于工具结果）
                9. **其他重要信息**：签证、货币、语言、通讯、购物、安全等（基于工具结果）
                
                **输出要求**：
                - 所有信息必须来源于工具执行结果
                - 如果工具执行失败，必须明确说明"工具执行失败，无法获取信息"
                - 对于工具未提供的信息，必须明确标注"需要进一步搜索"或"信息不足"
                - 不得为了完整性而编造任何细节
                - 确保总结包含足够的细节和具体信息，而不是泛泛而谈
                
                **重要提醒**：
                - 不要急于完成任务，确保搜索的充分性
                - 避免重复使用相同的搜索条件，但如果首次搜索结果不够，必须用不同关键词继续搜索
                - 必须获取帖子的详细内容，不要只依赖摘要
                - 如果信息不足，继续搜索比提供不完整的信息更重要
                """);
        } else {
            // 没有工具执行结果的情况：首次推理，根据任务描述和上下文开始执行
            userPrompt.append("""
                请根据以上任务描述和执行上下文，开始进行全面、深入的信息搜索。
                
                ** 严格数据来源要求**：
                - **必须严格基于工具执行结果**，不得编造、捏造任何信息
                - **禁止使用训练数据中的信息**，只能使用工具实际获取的数据
                - 如果工具执行失败或返回空结果，必须明确说明"未找到相关信息"
                - 所有推荐的地点、价格、联系方式等必须是工具实际获取的结果
                - 不得基于常识或经验补充任何未在工具结果中出现的信息
                
                **全面搜索策略**（必须遵循）：
                1. **多关键词搜索**：
                   - 使用目的地名称的多种形式搜索（全称、简称、英文名等）
                   - 结合不同主题搜索：如"XX攻略"、"XX旅游"、"XX景点"、"XX美食"、"XX住宿"、"XX交通"等
                   - 尝试不同角度：如"XX春季攻略"、"XX最佳时间"、"XX必去景点"、"XX必吃美食"等
                   - 尝试不同人群角度：如"XX家庭游"、"XX自由行"、"XX背包客攻略"等
                
                2. **深度内容获取**（关键步骤）：
                   - 使用search_feeds工具进行初步搜索，获取相关帖子列表
                   - **对于搜索结果中的每个相关帖子（至少前5-10个），必须使用get_feed_detail工具获取完整详细内容**
                   - **不要只依赖search_feeds返回的简短摘要，完整帖子内容通常包含更详细的信息**
                   - 如果某个帖子提供了特别有价值的信息，可以根据其内容继续搜索相关话题
                
                3. **详细信息补充**：
                   - 对于搜索到的具体景点、餐厅、地点，使用maps_search_detail获取详细信息
                   - 使用maps_text_search搜索特定地点或景点
                   - 使用maps_weather获取天气信息
                   - 使用search_locations、search_activities等工具获取相关信息
                
                4. **信息完整性检查**：
                   确保搜索涵盖以下所有维度：
                   - 目的地基础信息（概况、特色、文化、历史等）
                   - 景点信息（主要景点、特色景点、开放时间、位置等）
                   - 交通信息（到达方式、当地交通、交通卡等）
                   - 住宿信息（酒店、民宿、推荐区域等）
                   - 美食信息（当地特色、推荐餐厅、美食街等）
                   - 天气和最佳时间（气候特点、适宜月份等）
                   - 实用信息（签证、货币、语言、通讯、购物、安全等）
                   - 旅行路线和攻略（经典路线、不同时长路线等）
                   - 预算参考（不同消费档次、省钱攻略等）
                   - 特别提示（注意事项、避坑指南、文化礼仪等）
                
                **搜索执行顺序**：
                1. 首先使用search_feeds搜索多个关键词组合，获取相关帖子
                2. 对每个相关帖子使用get_feed_detail获取完整内容（至少5-10个）
                3. 从帖子内容中提取关键地点、景点、餐厅等信息
                4. 对提取的具体地点使用maps_search_detail、maps_text_search等工具获取详细信息
                5. 使用maps_weather获取天气信息
                6. 检查是否所有维度都有足够信息，如果不足，继续用不同关键词搜索补充
                
                **搜索质量要求**：
                - 不要满足于少量搜索结果，要搜索多个关键词和角度
                - 必须获取帖子的详细内容，不要只依赖摘要
                - 对于每个重要话题，都要从多个角度搜索
                - 确保信息的深度和广度都达到要求
                - 如果首次搜索结果不够充分，必须调整关键词继续搜索
                
                **输出要求**：
                - 所有信息必须来源于工具执行结果
                - 如果工具执行失败，必须明确说明"工具执行失败，无法获取信息"
                - 对于工具未提供的信息，必须明确标注"需要进一步搜索"或"信息不足"
                - 不得为了完整性而编造任何细节
                - 在决定完成任务前，确保已经进行了充分、深入的搜索
                
                **重要提醒**：
                - 搜索的充分性比速度更重要
                - 必须使用get_feed_detail获取帖子详细内容，这是获得充分信息的关键
                - 不要急于完成任务，确保所有维度都收集到足够信息
                - 如果信息不足，继续搜索比提供不完整的信息更重要
                """);
        }
        
        return userPrompt.toString();
    }
}
