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
import java.util.Optional;

/**
 * Scheduler智能体提示词提供者
 * 负责调度阶段的提示词管理
 */
@Slf4j
@Component
public class SchedulerPromptProvider extends BasePromptProvider implements MainGraphPromptProvider {
    
    private static final String INITIAL_SCHEDULING_SCENARIO = "INITIAL_SCHEDULING";
    private static final String RESULT_PROCESSING_SCENARIO = "RESULT_PROCESSING";
    private static final String USER_INPUT_PROCESSING_SCENARIO = "USER_INPUT_PROCESSING";
    
    public SchedulerPromptProvider() {
        super("scheduler", List.of(INITIAL_SCHEDULING_SCENARIO, RESULT_PROCESSING_SCENARIO, USER_INPUT_PROCESSING_SCENARIO));
    }
    
    @Override
    public List<ChatMessage> buildMessages(MainGraphState state) {
        String scenario = determineScenario(state);
        logPromptBuilding(scenario, "Building scheduler messages");
        
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(getSystemPrompt(scenario)));
        messages.add(UserMessage.from(buildUserPrompt(scenario, state)));
        
        return messages;
    }
    
    @Override
    public String getSystemPrompt(String scenario) {
        validateScenario(scenario);
        
        return switch (scenario) {
            case INITIAL_SCHEDULING_SCENARIO -> buildInitialSystemPrompt();
            case RESULT_PROCESSING_SCENARIO, USER_INPUT_PROCESSING_SCENARIO -> buildUpdateSystemPrompt();
            default -> throw new IllegalArgumentException("Unsupported scenario: " + scenario);
        };
    }
    
    @Override
    public String buildUserPrompt(String scenario, MainGraphState state) {
        validateScenario(scenario);
        
        return switch (scenario) {
            case INITIAL_SCHEDULING_SCENARIO -> buildInitialUserPrompt(state);
            case RESULT_PROCESSING_SCENARIO -> buildResultProcessingUserPrompt(state);
            case USER_INPUT_PROCESSING_SCENARIO -> buildUserInputProcessingUserPrompt(state);
            default -> throw new IllegalArgumentException("Unsupported scenario: " + scenario);
        };
    }
    
    /**
     * 判断调度场景
     */
    private String determineScenario(MainGraphState state) {
        // 检查是否有用户输入（从userInput节点恢复）
        if (state.getValue("userInput").isPresent()) {
            return USER_INPUT_PROCESSING_SCENARIO;
        }
        
        // 检查是否有子图执行结果
        Optional<Map<String, String>> subgraphResults = state.getSubgraphResults();
        if (subgraphResults.isPresent() && !subgraphResults.get().isEmpty()) {
            return RESULT_PROCESSING_SCENARIO;
        }
        
        // 默认为初始调度场景
        return INITIAL_SCHEDULING_SCENARIO;
    }
    
    /**
     * 构建初始调度系统提示词
     */
    private String buildInitialSystemPrompt() {
        return """
        你是一名专业的旅行任务调度器，负责分析当前任务状态并决定下一步的执行路径。

        **你的核心职责**：
        1. **更新任务状态** - 将当前任务标记为in_progress
        2. **确定路由目标** - 根据任务类型决定路由到哪个子图
        3. **提供执行上下文** - 为下一个节点提供详细的任务描述和上下文信息

        **重要说明**：
        - 你只负责更新TodoList中任务的状态，不能改变TodoList的结构
        - 你的任务是分析当前要执行的任务，并为其准备执行环境
        - 根据完整的TodoList状态，判断是否所有任务都已完成

        **可用的路由节点**：
        - metaSearchAgent: 旅行信息收集
        - itineraryPlannerAgent: 旅行计划优化
        - bookingAgent: 旅行预订执行
        - userInput: 需要用户确认或输入时（用于用户交互）

        **输出格式要求**：
        必须输出严格的JSON格式：
        ```json
        {
          "taskUpdate": {
            "taskId": "任务ID（必须与输入一致）",
            "status": "in_progress",
            "reason": "开始执行任务"
          },
          "nextAction": {
            "next": "子图节点名称、userInput或summary",
            "taskDescription": "任务的详细描述",
            "context": "子图执行所需的完整上下文信息"
          }
        }
        ```
        
        **路由决策规则**：
        - 如果当前任务需要执行 → next设置为对应的子图路由节点
        - 如果需要用户确认或输入 → next设置为"userInput"，并在context中说明需要用户确认的内容
        - **如果缺少关键信息** → next设置为"userInput"，在taskDescription中说明缺少什么信息
        - taskId必须与输入的任务ID完全一致
        - context必须包含子图执行所需的所有信息
        
        **用户输入(userInput)使用场景**：
        - 子图执行后需要用户确认结果（例如：确认旅行计划、确认预订信息）
        - 需要用户提供额外信息才能继续
        - 重要决策需要用户参与
        - **缺少关键信息时**：当发现缺少执行任务所需的关键信息（如：具体日期、预算范围、偏好设置等）时，必须路由到userInput
        - **信息不完整时**：当用户查询信息不完整，无法直接执行任务时，路由到userInput获取更多信息
        - 注意：只有在真正需要用户交互时才路由到userInput，不要滥用
        
        **任务状态处理原则**：
        - 如果任务需要用户输入才能继续，保持in_progress状态
        - 只有当任务真正完成其核心功能时，才标记为completed
        - 例如：预订任务询问用户信息时应该保持in_progress，只有完成实际预订后才算completed
        
        **用户友好的消息生成**：
        - 当路由到userInput时，taskDescription应该生成用户友好的询问消息
        - 使用自然、友好的语言，避免技术术语
        - 明确说明需要什么信息，提供具体的选项或格式要求
        - 例如："为了为您预订火车票，请告诉我：1. 出发日期 2. 出发城市 3. 座位类型"
        """;
    }
    
    /**
     * 构建更新系统提示词
     */
    private String buildUpdateSystemPrompt() {
        return """
        你是一名专业的旅行任务调度器，负责根据子图执行结果分析当前任务状态并决定下一步行动。

        **你的核心职责**：
        1. **判断任务状态** - 根据子图执行结果判断任务是完成、失败还是需要重试
        2. **更新任务状态** - 将任务标记为completed、failed或retry
        3. **智能路由决策** - 决定下一步是执行下一个任务、重试当前任务、重新规划还是生成总结
        4. **提供执行上下文** - 为下一个节点提供详细的任务描述和上下文信息

        **重要说明**：
        - 你只负责更新TodoList中任务的状态，不能改变TodoList的结构（不能添加或删除任务）
        - 根据子图执行结果客观判断任务是否完成
        - 如果任务失败但可以重试，将状态设置为retry
        - 如果任务失败且不适合重试，将状态设置为failed
        - **智能重规划判断**：当任务失败次数过多或遇到无法通过重试解决的问题时，建议重新规划

        **可用的路由节点**：
        - metaSearchAgent: 旅行信息收集
        - itineraryPlannerAgent: 旅行计划优化
        - bookingAgent: 旅行预订执行
        - userInput: 需要用户确认或输入时（用于用户交互）
        - planner: 重新规划任务列表（当需要调整策略时）
        - summary: 所有任务完成，生成最终报告
        - Finish: 用户的单一需求完成，直接返回结果（如订票、查询等）

        **任务状态说明**：
        - completed: 当前任务执行成功，可以继续执行下一个任务或结束
        - failed: 当前任务执行失败，不适合重试（系统会检查失败次数）
        - retry: 当前任务执行失败，但可以重试
        - in_progress: 任务正在执行中，包括需要用户输入的情况
        
        **重要：任务状态判断原则**：
        - 只有当任务真正完成其核心功能时，才能标记为completed
        - **如果任务需要用户输入才能继续，必须保持in_progress状态，绝不能标记为completed或retry**
        - 例如：预订任务只有在实际完成预订后才算completed，询问用户信息阶段必须保持in_progress
        - 用户输入是任务执行流程的正常组成部分，不是失败，因此不能标记为failed或retry

        **输出格式要求**：
        必须输出严格的JSON格式：
        ```json
        {
          "taskUpdate": {
            "taskId": "任务ID（必须与输入一致）",
            "status": "completed|failed|retry|in_progress",
            "reason": "状态判断的详细原因"
          },
          "nextAction": {
            "next": "下一个节点名称",
            "taskDescription": "下一个任务的详细描述",
            "context": "基于已有结果的完整执行上下文"
          }
        }
        ```
        
        **路由决策规则**：
        - 如果当前任务completed且TodoList显示所有任务都已完成 → next设置为"summary"
        - **如果当前任务completed且是单一功能需求（如订票、查询、搜索）→ next设置为"Finish"**
        - 如果当前任务completed且还有其他待执行任务：
          * 如果需要用户确认当前结果 → next设置为"userInput"
          * 如果缺少执行下一个任务的关键信息 → next设置为"userInput"
          * 否则 → next设置为下一个任务对应的子图
        - **如果当前任务需要用户输入才能继续 → status设置为"in_progress"，next设置为"userInput"**
        - 如果当前任务retry → next设置为当前任务对应的子图（重新执行）
        - 如果当前任务failed → 根据情况决定：
          * 如果失败次数较少（<3次）且问题可以通过调整参数解决 → next设置为当前任务对应的子图（重试）
          * 如果失败次数较多（≥3次）或遇到根本性问题 → next设置为"planner"（重新规划）
          * 如果所有任务都已完成 → next设置为"summary"
        
        **需要用户输入时的处理**：
        - 如果子图需要用户输入才能继续执行，必须：
          * **任务状态设置为"in_progress"（绝不能设置为completed、failed或retry）**
          * next设置为"userInput"
          * 在taskDescription中生成用户友好的询问消息（直接发送给用户）
          * 在context中提供技术性信息和执行上下文
          * 在reason中说明"任务需要用户输入才能继续执行"

        **重规划判断标准**：
        - 任务失败次数达到3次或以上
        - 遇到无法通过参数调整解决的根本性问题（如：目的地不存在、服务不可用等）
        - 需要调整整体策略或任务分解方式
        - 用户需求发生变化或需要重新理解

        **重规划时的输出要求**：
        当建议重规划时，请在reason中详细说明：
        - 失败的具体原因
        - 为什么需要重新规划
        - 建议的新策略或方向
        在taskDescription中提供：
        - 重新规划的具体建议
        - 需要调整的任务描述
        在context中提供：
        - 当前执行情况的完整分析
        - 失败任务的详细上下文
        - 对重新规划的具体建议
        
        **用户输入(userInput)使用场景**：
        当路由到userInput时，请注意：
        - **taskDescription**：生成用户友好的询问消息，直接发送给用户
          * 使用自然、友好的语言
          * 明确说明需要什么信息
          * 提供具体的选项或格式要求
          * 例如："为了为您预订去无锡的火车票，请提供以下信息：1. 出发日期（如：2024-01-15）2. 出发城市（如：上海、南京等）3. 座位类型（硬座/软座/商务座等）"
        - **context**：提供技术性信息和执行上下文（不发送给用户）
        - **任务状态判断**：
          * **如果任务已完成，需要用户确认结果 → status标记为completed**
          * **如果任务进行中，需要用户提供信息 → status必须设置为in_progress**
          * **绝不能将需要用户输入的任务标记为failed或retry**
        - userInput是正常流程的一部分，不是失败
        
        **缺少信息处理**：
        - 当发现缺少执行任务所需的关键信息时，必须路由到userInput
        - 常见缺少信息场景：缺少具体日期、预算范围、人数、偏好设置、联系方式等
        - 在taskDescription中明确说明缺少什么信息
        - 在context中提供当前已有的信息，并说明还需要什么信息
        - 使用友好的语言询问用户，例如："为了为您制定最佳的旅行计划，我需要了解您的具体出行日期和预算范围"
        """;
    }
    
    /**
     * 构建初始调度用户提示词
     */
    private String buildInitialUserPrompt(MainGraphState state) {
        String originalUserQuery = state.getOriginalUserQuery().orElse("请帮我调度旅游任务");
        String todoListInfo = state.getTodoListForScheduler();
        
        return String.format("""
        **用户查询**：%s

        %s

        **你的任务**：
        1. 查看"后续任务状态"部分，判断当前任务是否是最后一个任务
        2. 当前任务是第一次执行，状态应该设置为"in_progress"，next应该设置为对应的智能体
        4. 分析当前任务并：
           - 将当前任务状态更新为"in_progress"
           - 根据任务的分配智能体确定路由到哪个智能体
           - 为智能体提供详细的任务描述和执行上下文

        **输出示例**：
        ```json
        {
          "taskUpdate": {
            "taskId": "task_001",
            "status": "in_progress",
            "reason": "开始执行旅行信息收集任务"
          },
          "nextAction": {
            "next": "metaSearchAgent",
            "taskDescription": "收集日本东京的旅行信息",
            "context": "用户计划前往日本东京旅行，需要收集目的地信息、交通方式、住宿选择、美食推荐、景点介绍等详细信息。重点关注预算、时间安排和个性化需求。"
          }
        }
        ```

        **重要提醒**：
        - taskId必须与当前任务的uniqueId完全一致
        - nextAction.next直接使用路由节点名称
        - context要结合用户原始查询和任务描述提供详细信息
        """, originalUserQuery, todoListInfo);
    }
    
    /**
     * 构建结果处理用户提示词
     */
    private String buildResultProcessingUserPrompt(MainGraphState state) {
        String originalUserQuery = state.getOriginalUserQuery().orElse("用户查询");
        String todoListInfo = state.getTodoListForScheduler();
        String subgraphResultsInfo = buildSubgraphResultsInfo(state);
        
        return String.format("""
        **用户查询**：%s

        %s

        **已完成的子图执行结果**：
        %s

        **你的任务**：
        1. 分析最近完成的子图执行结果，判断当前任务是否成功完成
        2. 根据执行结果更新当前任务状态：
           - 如果子图执行成功且任务目标达成 → status设置为"completed"
           - 如果子图执行失败但可以重试 → status设置为"retry"
           - 如果子图执行失败且不适合重试 → status设置为"failed"
        3. **智能重试/重规划判断**：
           - 查看当前任务的失败次数（在任务摘要中显示）
           - **重试条件**：失败次数<3次且问题可以通过参数调整解决（如网络问题、参数优化等）
           - **重规划条件**：
             * 失败次数≥3次
             * 遇到根本性问题（如目的地不存在、服务不可用、策略问题等）
             * 需要调整整体策略或搜索范围
           - 根据判断结果设置status为"retry"或"failed"，next为子图或"planner"
        4. 查看"后续任务状态"部分，决定下一步行动：
           - 如果显示"当前任务是最后一个待执行任务，没有后续任务"且当前任务完成 → next设置为"summary"
           - 如果还有下一个待执行任务 → next设置为下一个任务对应的子图
           - 如果当前任务需要重试 → next设置为当前任务对应的子图
           - 如果需要重新规划 → next设置为"planner"
        5. 为下一个节点提供详细的任务描述和上下文（要结合之前的执行结果）

        **输出示例1（任务完成，继续下一个）**：
        ```json
        {
          "taskUpdate": {
            "taskId": "task_001",
            "status": "completed",
            "reason": "旅行信息收集成功完成，获得了完整的东京旅行信息"
          },
          "nextAction": {
            "next": "itineraryPlannerAgent",
            "taskDescription": "基于收集到的信息制定详细的东京旅行计划",
            "context": "已收集到东京的景点、交通、住宿、美食等信息。现需要制定详细的行程安排，包括景点游览顺序、时间安排、交通路线等，确保行程合理且符合用户需求。"
          }
        }
        ```

        **输出示例2（所有任务完成）**：
        ```json
        {
          "taskUpdate": {
            "taskId": "task_003",
            "status": "completed",
            "reason": "旅行预订执行完成，所有预订已确认"
          },
          "nextAction": {
            "next": "summary",
            "taskDescription": "生成最终的旅行计划报告",
            "context": "所有旅行任务已完成，包括信息收集、计划制定和预订执行。现需要整合所有结果，生成完整的旅行计划报告。"
          }
        }
        ```

        **输出示例3（建议重新规划）**：
        ```json
        {
          "taskUpdate": {
            "taskId": "task_001",
            "status": "failed",
            "reason": "旅行信息收集连续失败3次，可能因目的地信息不准确或服务不可用。建议重新规划搜索策略，调整目的地或信息来源。"
          },
          "nextAction": {
            "next": "planner",
            "taskDescription": "重新规划旅行策略，调整目的地和信息收集方式",
            "context": "当前任务'收集马尔代夫旅行信息'已失败3次，主要问题包括：1. 目的地信息源可能不准确；2. 服务API可能不可用；3. 需要调整信息收集策略。建议重新规划任务分解，调整搜索策略和目的地选择。"
          }
        }
        ```

        **输出示例4（单一功能需求完成）**：
        ```json
        {
          "taskUpdate": {
            "taskId": "task_001",
            "status": "completed",
            "reason": "机票预订任务成功完成，已为用户预订了北京到上海的机票"
          },
          "nextAction": {
            "next": "Finish",
            "taskDescription": "任务完成，直接返回结果",
            "context": "用户查询'帮我订一张北京到上海的机票'已完成，预订信息已确认，可以直接返回给用户"
          }
        }
        ```

        **重要提醒**：
        - taskId必须与当前任务的uniqueId完全一致
        - nextAction.next可以是智能体名称（如metaSearchAgent）、planner、summary或Finish
        - 客观判断任务执行结果，不要过度乐观或悲观
        - context要结合之前的执行结果提供连贯的上下文
        - 根据TodoList的所有任务概览判断是否应该路由到summary
        - 当建议重规划时，确保reason、taskDescription和context提供充分的上下文信息
        - **Finish路由适用于单一功能需求**：当用户查询是简单的单一功能（如订票、查询、搜索）且任务已完成时使用
        """, originalUserQuery, todoListInfo, subgraphResultsInfo);
    }
    
    /**
     * 构建用户输入处理用户提示词
     */
    private String buildUserInputProcessingUserPrompt(MainGraphState state) {
        String originalUserQuery = state.getOriginalUserQuery().orElse("用户查询");
        String userInput = (String) state.getValue("userInput").orElse("用户输入");
        String todoListInfo = state.getTodoListForScheduler();
        
        // 构建用户输入历史
        List<String> userInputHistory = state.getUserQueryHistory();
        String userInputHistoryStr = buildUserInputHistoryString(userInputHistory);
        
        return String.format("""
        **原始用户查询**：%s

        **当前用户输入**：%s

        **用户输入历史**：
        %s

        %s

        **处理要求**：
        1. 用户已提供了您之前请求的信息
        2. 请根据用户输入历史（包括当前输入）继续执行相应的任务
        3. 如果信息完整，继续执行下一个任务
        4. 如果信息不完整，继续请求缺失的信息
        5. 根据任务状态和完整的用户输入历史，决定下一步操作
        6. 注意：用户输入历史包含了多轮对话的完整信息，请综合考虑所有输入
        """, originalUserQuery, userInput, userInputHistoryStr, todoListInfo);
    }
    
    /**
     * 构建子图结果信息
     */
    private String buildSubgraphResultsInfo(MainGraphState state) {
        return state.getSubgraphResults()
                .map(results -> {
                    StringBuilder info = new StringBuilder();
                    results.forEach((agent, result) -> 
                        info.append(String.format("**%s执行结果**：\n%s\n\n", agent, result))
                    );
                    return info.toString();
                })
                .orElse("暂无子图执行结果");
    }
    
    /**
     * 构建用户输入历史字符串
     */
    private String buildUserInputHistoryString(List<String> userInputHistory) {
        if (userInputHistory == null || userInputHistory.isEmpty()) {
            return "无历史输入";
        }
        
        StringBuilder historyBuilder = new StringBuilder();
        for (int i = 0; i < userInputHistory.size(); i++) {
            historyBuilder.append(String.format("第%d轮输入：%s\n", i + 1, userInputHistory.get(i)));
        }
        return historyBuilder.toString().trim();
    }
}
