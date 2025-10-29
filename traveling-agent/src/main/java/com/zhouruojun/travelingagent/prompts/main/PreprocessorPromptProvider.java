package com.zhouruojun.travelingagent.prompts.main;

import com.zhouruojun.travelingagent.agent.state.MainGraphState;
import com.zhouruojun.travelingagent.prompts.BasePromptProvider;
import com.zhouruojun.travelingagent.prompts.MainGraphPromptProvider;
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
 * 预处理器智能体提示词提供者
 * 负责处理简单对话和任务分类
 */
@Slf4j
@Component
public class PreprocessorPromptProvider extends BasePromptProvider implements MainGraphPromptProvider {

    private static final String DEFAULT_SCENARIO = "DEFAULT";

    public PreprocessorPromptProvider() {
        super("preprocessor", List.of(DEFAULT_SCENARIO));
    }

    @Override
    public String getAgentName() {
        return "preprocessor";
    }

    @Override
    public List<String> getSupportedScenarios() {
        return List.of("DEFAULT");
    }
    
    @Override
    public String buildUserPrompt(String scenario, MainGraphState state) {
        validateScenario(scenario);
        return buildDefaultUserPrompt(state);
    }

    @Override
    public List<ChatMessage> buildMessages(MainGraphState state) {
        String scenario = determineScenario(state);
        logPromptBuilding(scenario, "Building preprocessor messages");

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(getSystemPrompt(scenario)));
        messages.add(UserMessage.from(buildUserPrompt(scenario, state)));

        return messages;
    }

    @Override
    public String getSystemPrompt(String scenario) {
        String basePrompt = """
                你是一个智能预处理器，负责处理用户的旅游相关查询并进行任务分类。
                
                ## 你的核心职责
                1. **日常对话**：与用户进行自然、友好的日常对话
                2. **工具调用**：当用户询问具体信息时，直接调用相应工具（天气、酒店、航班、地点、活动等）
                3. **信息收集**：对于复杂任务，主动询问缺失的关键信息
                4. **智能路由**：根据任务复杂度和信息完整性决定下一步行动
                5. **上下文理解**：仔细分析完整对话历史，理解用户的真实意图和当前需求
                
                ## 任务分类标准
                - **简单任务**：单一查询，可以直接回答或调用简单工具
                  - 天气查询："北京今天天气怎么样？"
                  - 酒店查询："上海有什么好的酒店推荐？"
                  - 机票查询："北京到上海的机票价格"
                  - 帖子搜索："推荐一些旅游攻略"
                
                - **复杂任务**：需要多步骤规划的任务
                  - "请帮我规划去北京的旅游计划"
                  - "我想制定一个7天的日本旅游行程"
                  - "帮我安排一次完整的欧洲之旅"
                
                ## 复杂任务信息充足性判断
                对于复杂任务，需要收集以下关键信息：
                - **目的地**：具体城市或国家
                - **时间**：出发时间、旅行天数
                - **预算**：大概预算范围
                - **偏好**：住宿类型、交通方式、活动偏好等
                
                如果信息不足，主动询问用户，next设置为"Finish"
                
                ## 输出格式
                你有两种输出方式：
                
                **方式1：直接调用工具**（需要查询信息时）
                - 当用户询问天气、酒店、航班、地点、活动等信息时，直接调用相应工具
                - 当收到工具执行结果后，如果还需要更多信息，继续调用相应工具
                - 直接调用工具，不需要返回JSON格式
                - 工具调用会自动执行，结果会返回给你
                - 如果工具请求有多个，如果没有前后依赖关系，可以并行执行！
                
                **方式2：JSON格式输出**（任务完成时）
                - 当收到工具执行结果后，如果已满足用户需求，使用JSON格式输出
                - 当不需要调用工具时，直接使用JSON格式输出
                ```json
                {
                  "next": "planner|Finish",
                  "output": "具体的任务描述或最终回复"
                }
                ```
                
                **推理流程**：
                1. 分析用户需求 → 调用工具 → 分析工具结果 → 判断是否满足用户需求
                2. 如果还需要更多信息 → 继续调用工具（方式1）
                3. 如果已满足用户需求 → 使用JSON格式输出（方式2）
                
                ## 路由规则
                - **调用工具**：需要查询信息时，直接调用工具（如 maps_weather, search_locations 等）
                - **next: "Finish"**：
                  - 简单任务已完成，直接回答用户
                  - 复杂任务信息不足，需要向用户询问更多信息
                  - 用户只是打招呼或询问系统能力，进行日常对话
                - **next: "planner"**：复杂任务信息充足，进入完整规划流程
                  - output必须包含：目的地、时间、预算、偏好等完整信息
                  - 为planner提供详细的任务描述和上下文
                  - 注意：只有用户明确提出了具体的旅游规划需求且信息充足时，才使用planner
                
                ## 路由示例

                **使用Finish的情况：**
                - 用户："你好" → next: "Finish", output: "你好！我是你的旅游助手..."
                - 用户："你能做什么？" → next: "Finish", output: "我可以帮你规划行程、查询天气..."
                - 用户："我想去北京旅游" → next: "Finish", output: "好的！请告诉我你的出发时间..."
                
                **使用planner的情况：**
                - 用户："我想去北京旅游，3月15日出发，5天行程，预算5000元，喜欢历史文化" 
                  → next: "planner", output: "用户需求：北京5天旅游规划，3月15日出发，预算5000元，偏好历史文化景点"
                
                ## 对话上下文处理
                1. **分析完整对话历史**：仔细阅读"完整对话历史"部分，理解整个对话的上下文
                2. **理解用户意图**：结合当前查询和对话历史，准确理解用户的真实需求
                3. **处理简短回复**：当用户输入简短词汇（如"需要"、"是的"、"好的"等）时，要结合上下文理解其含义
                4. **保持对话连贯性**：确保回复与之前的对话内容保持一致和连贯
                
                ## 工具结果处理流程
                1. **收到工具执行结果**：分析工具返回的信息
                2. **判断是否满足用户需求**：检查工具结果是否已回答用户的问题
                3. **决定下一步行动**：
                   - 如果还需要更多信息 → 继续调用相应工具
                   - 如果已满足用户需求 → 使用JSON格式输出最终回复
                4. **循环执行**：工具调用后会再次收到结果，重复上述流程直到满足用户需求
                
                ## 注意事项
                1. 保持友好、自然的对话风格
                2. 对于简单查询，尽量直接回答或调用相关工具
                3. 对于复杂任务，主动收集关键信息后再路由到planner
                4. 确保JSON格式正确，避免解析错误
                5. 在询问信息时，要具体明确，避免模糊问题
                6. **重要**：只有用户明确提出了具体的旅游规划需求且信息充足时，才使用planner
                7. **工具并行执行**：如果需要调用多个工具，前后没有依赖关系的工具可以并行调用
                8. **工具调用循环**：可以多次调用工具直到满足用户需求
                9. **输出格式**：只有在满足用户需求时才使用JSON格式，继续调用工具时直接调用工具
                10. **上下文理解**：必须仔细分析完整对话历史，理解用户的真实意图，特别是对简短回复的理解
                11. **对话连贯性**：确保回复与之前的对话内容保持一致，避免重复询问或误解用户意图
                12. **第一轮对话**：第一轮对话时不显示对话历史，从第二轮开始显示完整对话历史
                13. **需求满足判断**：仔细分析工具执行结果是否已完全满足用户的查询需求
                """;
        
        return addTimeInfoToSystemPrompt(basePrompt);
    }

    /**
     * 判断执行场景
     */
    private String determineScenario(MainGraphState state) {
        // Preprocessor目前只有一个默认场景
        return DEFAULT_SCENARIO;
    }

    /**
     * 构建默认用户提示词
     */
    private String buildDefaultUserPrompt(MainGraphState state) {
        StringBuilder prompt = new StringBuilder();
        
        // 检查是否是第一轮对话（没有AI回复）
        boolean isFirstRound = !hasAiMessages(state);
        
        // 添加完整的对话历史（只在非第一轮时添加）
        if (!isFirstRound) {
            String conversationHistory = buildConversationHistory(state);
            if (!conversationHistory.isEmpty()) {
                prompt.append("**完整对话历史**：\n").append(conversationHistory).append("\n\n");
            }
        }
        
        // 添加用户查询历史（作为补充）
        String userQueryHistory = state.getFormattedUserQueryHistory();
        if (!userQueryHistory.isEmpty()) {
            prompt.append("**用户查询历史**：\n").append(userQueryHistory).append("\n\n");
        }
        
        // 添加任务响应历史（非空情况下）
        String taskResponseHistory = state.getFormattedConversationHistory();
        if (!taskResponseHistory.isEmpty()) {
            prompt.append("**任务处理历史**：\n").append(taskResponseHistory).append("\n\n");
        }
        
        // 添加预处理器响应历史（非空情况下）
        String preprocessorResponseHistory = getFormattedPreprocessorResponseHistory(state);
        if (!preprocessorResponseHistory.isEmpty()) {
            prompt.append("**预处理器响应历史**：\n").append(preprocessorResponseHistory).append("\n\n");
        }
        
        // 添加工具执行结果
        Optional<String> toolExecutionResult = state.getToolExecutionResult();
        if (toolExecutionResult.isPresent() && !toolExecutionResult.get().trim().isEmpty()) {
            prompt.append("**工具执行结果**：\n").append(toolExecutionResult.get()).append("\n\n");
        }
        
        // 添加当前用户查询
        String currentQuery = getCurrentUserQuery(state);
        if (!currentQuery.isEmpty()) {
            prompt.append("**当前用户查询**：").append(currentQuery).append("\n\n");
        }
        
        // 最后添加输出约束提醒（只有在有工具执行结果时才添加）
        if (toolExecutionResult.isPresent() && !toolExecutionResult.get().trim().isEmpty()) {
            prompt.append("**重要提醒**：基于上述工具执行结果，请判断是否满足用户需求：\n");
            prompt.append("1. **如果工具结果已满足用户需求**：使用JSON格式输出最终回复\n");
            prompt.append("2. **如果当前的工具执行结果不能完全回答用户的需求**：继续调用相应工具\n");
            prompt.append("3. **如果没有工具可以解决用户的需求请诚实得告诉用户**\n");
//            prompt.append("\n**JSON格式示例**：\n");
//            prompt.append("```json\n");
//            prompt.append("{\n");
//            prompt.append("  \"next\": \"planner|Finish\",\n");
//            prompt.append("  \"output\": \"基于工具结果的回复内容\"\n");
//            prompt.append("}\n");
//            prompt.append("```\n\n");
        }

        return prompt.toString();
    }
    
    /**
     * 检查是否有AI消息（判断是否为第一轮对话）
     */
    private boolean hasAiMessages(MainGraphState state) {
        return state.messages().stream()
                .anyMatch(msg -> msg instanceof AiMessage);
    }
    
    /**
     * 构建完整的对话历史
     */
    private String buildConversationHistory(MainGraphState state) {
        List<ChatMessage> messages = state.messages();
        if (messages.isEmpty()) {
            return "";
        }
        
        StringBuilder history = new StringBuilder();
        int round = 1;
        
        for (int i = 0; i < messages.size(); i++) {
            ChatMessage message = messages.get(i);
            
            if (message instanceof UserMessage) {
                UserMessage userMsg = (UserMessage) message;
                history.append(String.format("【第%d轮对话】\n", round));
                history.append(String.format("用户: %s\n", userMsg.singleText()));
                
                // 查找对应的AI回复
                if (i + 1 < messages.size() && messages.get(i + 1) instanceof dev.langchain4j.data.message.AiMessage) {
                    dev.langchain4j.data.message.AiMessage aiMsg = (dev.langchain4j.data.message.AiMessage) messages.get(i + 1);
                    history.append(String.format("系统: %s\n\n", 
                        truncateIfTooLong(aiMsg.text(), 500)));
                    i++; // 跳过已处理的AI消息
                } else {
                    history.append("系统: [等待回复]\n\n");
                }
                round++;
            }
        }
        
        return history.toString();
    }
    
    /**
     * 截断过长的文本
     */
    private String truncateIfTooLong(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "...（后续内容省略）";
    }
    
    /**
     * 获取格式化的预处理器响应历史
     */
    private String getFormattedPreprocessorResponseHistory(MainGraphState state) {
        List<String> responses = state.getPreprocessorResponseHistory();
        
        if (responses.isEmpty()) {
            return "";
        }
        
        StringBuilder history = new StringBuilder();
        for (int i = 0; i < responses.size(); i++) {
            history.append(String.format("【第%d轮预处理器回复】\n", i + 1));
            history.append(String.format("系统: %s\n\n", 
                truncateIfTooLong(responses.get(i), 500)));
        }
        
        return history.toString();
    }
    
    /**
     * 获取当前用户查询
     */
    private String getCurrentUserQuery(MainGraphState state) {
        return state.messages().stream()
                .filter(msg -> msg instanceof UserMessage)
                .map(msg -> ((UserMessage) msg).singleText())
                .reduce((first, second) -> second) // 获取最后一个
                .orElse(state.getOriginalUserQuery().orElse(""));
    }
}
