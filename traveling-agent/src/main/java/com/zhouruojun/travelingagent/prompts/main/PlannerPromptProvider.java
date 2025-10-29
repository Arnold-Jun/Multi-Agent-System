package com.zhouruojun.travelingagent.prompts.main;

import com.zhouruojun.travelingagent.agent.state.MainGraphState;
import com.zhouruojun.travelingagent.agent.state.main.TodoList;
import com.zhouruojun.travelingagent.agent.state.main.TodoTask;
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
import java.util.Map;
import java.util.Optional;

/**
 * Planner智能体提示词提供者
 * 负责规划阶段的提示词管理
 */
@Slf4j
@Component
public class PlannerPromptProvider extends BasePromptProvider implements MainGraphPromptProvider {
    
    private static final String INITIAL_SCENARIO = "INITIAL";
    private static final String REPLAN_SCENARIO = "REPLAN";
    private static final String CONTINUATION_SCENARIO = "CONTINUATION";
    private static final String JSON_PARSE_ERROR_SCENARIO = "JSON_PARSE_ERROR";
    
    public PlannerPromptProvider() {
        super("planner", List.of(INITIAL_SCENARIO, REPLAN_SCENARIO, CONTINUATION_SCENARIO, JSON_PARSE_ERROR_SCENARIO));
    }
    
    @Override
    public List<ChatMessage> buildMessages(MainGraphState state) {
        String scenario = determineScenario(state);
        logPromptBuilding(scenario, "Building planner messages");
        
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(getSystemPrompt(scenario)));
        messages.add(UserMessage.from(buildUserPrompt(scenario, state)));
        
        return messages;
    }
    
    @Override
    public String getSystemPrompt(String scenario) {
        validateScenario(scenario);
        
        return switch (scenario) {
            case INITIAL_SCENARIO -> buildInitialSystemPrompt();
            case REPLAN_SCENARIO -> buildReplanSystemPrompt();
            case CONTINUATION_SCENARIO -> buildContinuationSystemPrompt();
            case JSON_PARSE_ERROR_SCENARIO -> buildJsonParseErrorSystemPrompt();
            default -> throw new IllegalArgumentException("Unsupported scenario: " + scenario);
        };
    }
    
    @Override
    public String buildUserPrompt(String scenario, MainGraphState state) {
        validateScenario(scenario);
        
        return switch (scenario) {
            case INITIAL_SCENARIO -> buildInitialUserPrompt(state);
            case REPLAN_SCENARIO -> buildReplanUserPrompt(state);
            case CONTINUATION_SCENARIO -> buildContinuationUserPrompt(state);
            case JSON_PARSE_ERROR_SCENARIO -> buildJsonParseErrorUserPrompt(state);
            default -> throw new IllegalArgumentException("Unsupported scenario: " + scenario);
        };
    }
    
    /**
     * 判断执行场景
     */
    private String determineScenario(MainGraphState state) {
        // 检查是否有taskResponseHistory（继续对话场景）
        List<String> taskResponseHistory = state.getTaskResponseHistory();
        if (!taskResponseHistory.isEmpty()) {
            return CONTINUATION_SCENARIO;
        }
        
        // 检查是否有重新规划消息
        if (hasReplanMessage(state)) {
            return REPLAN_SCENARIO;
        }
        
        // 检查是否有JSON解析错误消息
        if (hasJsonParseError(state)) {
            return JSON_PARSE_ERROR_SCENARIO;
        }
        
        // 默认为初始规划场景
        return INITIAL_SCENARIO;
    }
    
    /**
     * 检查是否有重新规划消息
     */
    private boolean hasReplanMessage(MainGraphState state) {
        return state.lastMessage()
                .filter(msg -> msg instanceof UserMessage)
                .map(msg -> ((UserMessage) msg).singleText())
                .filter(text -> text.contains("【Scheduler建议重新规划】"))
                .isPresent();
    }
    
    /**
     * 检查是否有JSON解析错误消息
     */
    private boolean hasJsonParseError(MainGraphState state) {
        return state.lastMessage()
            .filter(msg -> msg instanceof AiMessage)
            .map(msg -> ((AiMessage) msg).text())
            .filter(text -> text.contains("JSON解析失败"))
            .isPresent();
    }
    
    /**
     * 构建初始规划系统提示词
     */
    private String buildInitialSystemPrompt() {
        String basePrompt = """
        你是一名专业的旅行任务规划器，负责分析用户需求并将复杂旅行任务分解为可执行的任务列表。

        **你的职责**：
        - 深入理解用户的旅行需求
        - 将复杂旅行任务分解为具体的子任务
        - 为每个子任务分配合适的子智能体
        - 识别任务间的依赖关系
        - 输出JSON格式的任务列表

        **可用的子智能体**：
        - metaSearchAgent: 旅行信息收集（目的地信息、交通、住宿、美食、活动、攻略等）
        - itineraryPlannerAgent: 旅行计划优化（路线规划、行程优化、个性化定制）
        - bookingAgent: 火车票，机票及酒店具体信息查询，旅行预订执行（机票、酒店、门票、租车等预订）

        **任务分配指导**：
        1. **旅行信息收集任务** → metaSearchAgent
        2. **旅行计划优化任务** → itineraryPlannerAgent
        3. **旅行预订执行任务** → bookingAgent

        **重要约束**：
        - **预订任务判断**：只有当用户明确要求预订服务时才分配bookingAgent任务
        - **信息收集优先**：如果用户只是询问信息、制定计划等，只分配metaSearchAgent和itineraryPlannerAgent任务
        - **避免过度规划**：不要为用户没有明确要求的服务类型分配预订任务

        **任务合并规则**：
        - 如果连续多个子任务都分配给同一个子智能体，必须合并成一个任务
        - 合并后的任务描述要包含所有子任务的要求

        **输出格式要求**：
        你必须输出以下JSON格式：
        {
          "add": [
            {
              "description": "新任务描述",
              "assignedAgent": "agentName",
              "status": "pending",
              "order": 任务顺序
            }
          ]
        }

        **重要**：
        - 只输出JSON，不要其他内容
        - 确保任务描述清晰具体
        - 合理分配任务顺序
        """;
        
        return addTimeInfoToSystemPrompt(basePrompt);
    }
    
    /**
     * 构建重新规划系统提示词
     */
    private String buildReplanSystemPrompt() {
        String basePrompt = """
        你是一名专业的旅行任务规划器，负责根据Scheduler的建议进行重新规划。

        **你的职责**：
        - 分析Scheduler提供的重新规划建议
        - 理解任务失败的原因和当前状态
        - 基于失败原因调整任务策略
        - 重新规划任务列表以解决问题
        - 输出JSON格式的任务列表

        **重新规划原则**：
        - 仔细分析失败原因，调整任务策略
        - 考虑Scheduler的建议和上下文信息
        - 可能需要添加新的任务来解决之前的问题
        - 通过modify操作更新失败任务的状态
        - 确保任务顺序合理，避免重复失败

        **输出格式要求**：
        你必须输出以下JSON格式：
        {
          "add": [
            {
              "description": "新任务描述",
              "assignedAgent": "agentName",
              "status": "pending",
              "order": 任务顺序
            }
          ],
          "modify": [
            {
              "uniqueId": "要修改的任务ID",
              "status": "completed|failed|pending"
            }
          ]
        }

        **重要**：
        - 只输出JSON，不要其他内容
        - 不要删除失败的任务，保留完整的执行历史
        - 通过modify操作更新任务状态
        """;
        
        return addTimeInfoToSystemPrompt(basePrompt);
    }
    
    /**
     * 构建JSON解析错误系统提示词
     */
    private String buildJsonParseErrorSystemPrompt() {
        String basePrompt = """
        你是一名专业的旅行任务规划器，负责处理JSON解析错误并重新生成正确的任务列表。

        **你的职责**：
        - 分析JSON解析错误的原因
        - 重新生成符合格式要求的任务列表
        - 确保输出正确的JSON格式

        **输出格式要求**：
        你必须输出以下JSON格式：
        {
          "add": [
            {
              "description": "新任务描述",
              "assignedAgent": "agentName",
              "status": "pending",
              "order": 任务顺序
            }
          ],
          "modify": [
            {
              "uniqueId": "要修改的任务ID",
              "status": "completed|failed|pending"
            }
          ]
        }

        **重要**：
        - 只输出JSON，不要其他内容
        - 确保JSON格式完全正确
        - 修复之前的格式错误
        """;
        
        return addTimeInfoToSystemPrompt(basePrompt);
    }
    
    /**
     * 构建初始规划用户提示词
     */
    private String buildInitialUserPrompt(MainGraphState state) {
        // 使用taskHistory中的最新任务，如果没有则使用originalUserQuery
        List<String> taskHistory = state.getTaskHistory();
        String userQuery = taskHistory.isEmpty() ? 
            state.getOriginalUserQuery().orElse("请帮我制定旅游计划") : 
            taskHistory.get(taskHistory.size() - 1);
        
        return String.format("""
        **任务描述**：%s

        **输出格式**：
        你必须输出以下JSON格式：
        {
          "add": [
            {
              "description": "任务描述",
              "assignedAgent": "agentName",
              "status": "pending",
              "order": 1
            }
          ]
        }

        **重要**：
        - 任务顺序很重要，按执行顺序设置order
        - 如果连续多个子任务都分配给同一个子智能体，必须合并成一个任务
        - 确保任务描述清晰明确
        - 只输出JSON，不要其他内容
        """, userQuery);
    }
    
    /**
     * 构建重新规划用户提示词
     */
    private String buildReplanUserPrompt(MainGraphState state) {
        // 使用taskHistory中的最新任务，如果没有则使用originalUserQuery
        List<String> taskHistory = state.getTaskHistory();
        String userQuery = taskHistory.isEmpty() ? 
            state.getOriginalUserQuery().orElse("用户查询") : 
            taskHistory.get(taskHistory.size() - 1);
        String todoListInfo = state.getTodoListStatistics();
        Map<String, String> subgraphResults = state.getSubgraphResults().orElse(Map.of());
        
        String subgraphInfo = formatSubgraphResults(subgraphResults);
        String conversationHistory = buildConversationHistory(state);
        
        return String.format("""
        **对话历史**：
        %s

        **当前任务描述**：%s

        **当前任务列表状态**：
        %s

        **子图执行结果**：
        %s

        **重新规划要求**：
        请根据Scheduler的建议和失败原因，重新规划任务列表。重点关注：
        1. 分析失败原因，调整任务策略
        2. 考虑Scheduler提供的建议和上下文
        3. 可能需要添加新任务来解决之前的问题
        4. 保留失败任务的历史记录，通过modify操作更新状态
        5. 确保任务顺序合理，避免重复失败

        **输出格式**：
        你必须输出以下JSON格式：
        {
          "add": [
            {
              "description": "新任务描述",
              "assignedAgent": "agentName",
              "status": "pending",
              "order": 任务顺序
            }
          ],
          "modify": [
            {
              "uniqueId": "要修改的任务ID",
              "status": "completed|failed|pending"
            }
          ]
        }

        **重要**：
        - 仔细分析Scheduler的建议和失败原因
        - 根据失败原因调整任务策略
        - 确保任务顺序合理，避免重复失败
        - 只输出JSON，不要其他内容
        """, conversationHistory, userQuery, todoListInfo, subgraphInfo);
    }
    
    /**
     * 构建对话历史
     */
    private String buildConversationHistory(MainGraphState state) {
        List<String> taskHistory = state.getTaskHistory();
        List<String> taskResponseHistory = state.getTaskResponseHistory();
        
        StringBuilder history = new StringBuilder();
        
        // 构建对话历史
        int maxRounds = Math.max(taskHistory.size(), taskResponseHistory.size());
        for (int i = 0; i < maxRounds; i++) {
            history.append(String.format("【第%d轮任务】\n", i + 1));
            
            // 添加任务描述
            if (i < taskHistory.size()) {
                history.append(String.format("任务: %s\n", taskHistory.get(i)));
            }
            
            // 添加系统响应
            if (i < taskResponseHistory.size()) {
                history.append(String.format("系统: %s\n", taskResponseHistory.get(i)));
            }
            
            history.append("\n");
        }
        
        return history.toString();
    }

    /**
     * 构建JSON解析错误用户提示词
     */
    private String buildJsonParseErrorUserPrompt(MainGraphState state) {
        String lastJsonOutput = extractLastJsonOutput(state);
        String errorMessage = extractJsonErrorMessage(state);
        
        StringBuilder prompt = new StringBuilder();
        prompt.append("JSON解析失败: ").append(errorMessage).append("\n\n");
        
        if (lastJsonOutput != null && !lastJsonOutput.trim().isEmpty()) {
            prompt.append("**上一次的JSON输出（有问题的）**：\n");
            prompt.append("```json\n");
            prompt.append(lastJsonOutput);
            prompt.append("\n```\n\n");
        }
        
        prompt.append("**请根据以上信息重新输出正确的JSON格式**：\n");
        prompt.append("1. 分析上一次JSON输出的问题\n");
        prompt.append("2. 根据错误信息修正JSON格式\n");
        prompt.append("3. 确保JSON语法完全正确\n");
        prompt.append("4. 输出标准的JSON格式，包含add、modify、delete字段\n");
        
        return prompt.toString();
    }
    
    /**
     * 提取最后一次的JSON输出
     */
    private String extractLastJsonOutput(MainGraphState state) {
        return state.lastMessage()
            .filter(msg -> msg instanceof AiMessage)
            .map(msg -> ((AiMessage) msg).text())
            .orElse(null);
    }
    
    /**
     * 提取JSON错误信息
     */
    private String extractJsonErrorMessage(MainGraphState state) {
        return state.lastMessage()
            .filter(msg -> msg instanceof AiMessage)
            .map(msg -> ((AiMessage) msg).text())
            .filter(text -> text.contains("JSON解析失败"))
            .orElse("JSON格式错误");
    }
    
    /**
     * 构建继续对话系统提示词
     */
    private String buildContinuationSystemPrompt() {
        String basePrompt = """
        你是一名专业的旅行任务规划器，负责处理用户的后续查询和需求。

        **你的职责**：
        - 理解用户的后续查询需求
        - 结合之前的对话历史和任务执行结果
        - 分析新需求与之前任务的关系
        - 决定是否需要添加新任务或修改现有任务
        - 输出JSON格式的任务列表

        **处理原则**：
        - 仔细分析用户的后续查询，理解其真实意图
        - 结合之前的对话历史和任务执行结果
        - 判断新需求是否与之前的任务相关
        - 如果需要新任务，合理分配任务顺序
        - 保持任务的连续性和逻辑性

        **输出格式要求**：
        你必须输出以下JSON格式：
        {
          "add": [
            {
              "description": "新任务描述",
              "assignedAgent": "agentName",
              "status": "pending",
              "order": 任务顺序
            }
          ],
          "modify": [
            {
              "uniqueId": "要修改的任务ID",
              "status": "completed|failed|pending"
            }
          ]
        }

        **重要**：
        - 只输出JSON，不要其他内容
        - 确保任务描述清晰具体
        - 合理分配任务顺序
        - 考虑与之前任务的关联性
        """;
        
        return addTimeInfoToSystemPrompt(basePrompt);
    }
    
    /**
     * 构建继续对话用户提示词
     */
    private String buildContinuationUserPrompt(MainGraphState state) {
        String conversationHistory = state.getFormattedConversationHistory();
        // 使用taskHistory中的最新任务，如果没有则使用latestUserQuery
        List<String> taskHistory = state.getTaskHistory();
        String currentQuery = taskHistory.isEmpty() ? 
            state.getLatestUserQuery() : 
            taskHistory.get(taskHistory.size() - 1);
        String todoListInfo = getDetailedTodoListInfo(state);
        Map<String, String> subgraphResults = state.getSubgraphResults().orElse(Map.of());
        
        String subgraphInfo = formatSubgraphResults(subgraphResults);
        
        return String.format("""
        **对话历史**：
        %s

        **当前任务描述**：%s

        **当前任务列表状态**：
        %s

        **子图执行结果**：
        %s

        **处理要求**：
        请根据用户的后续查询，结合之前的对话历史和任务执行结果，分析新需求并决定：
        1. 用户的后续查询与之前任务的关系
        2. 是否需要添加新的任务来满足新需求
        3. 新任务的合理顺序和分配
        4. 是否需要修改现有任务的状态

        **输出格式**：
        你必须输出以下JSON格式：
        {
          "add": [
            {
              "description": "新任务描述",
              "assignedAgent": "agentName",
              "status": "pending",
              "order": 任务顺序
            }
          ],
          "modify": [
            {
              "uniqueId": "要修改的任务ID",
              "status": "completed|failed|pending"
            }
          ]
        }

        **重要**：
        - 仔细分析用户的后续查询意图
        - 结合对话历史和任务执行结果
        - 确保任务描述清晰明确
        - 只输出JSON，不要其他内容
        """, conversationHistory, currentQuery, todoListInfo, subgraphInfo);
    }
    
    /**
     * 获取详细的TodoList信息
     */
    private String getDetailedTodoListInfo(MainGraphState state) {
        Optional<TodoList> todoListOpt = state.getTodoList();
        if (todoListOpt.isEmpty()) {
            return "暂无任务列表";
        }
        
        TodoList todoList = todoListOpt.get();
        StringBuilder info = new StringBuilder();
        
        // 添加统计信息
        info.append(state.getTodoListStatistics()).append("\n\n");
        
        // 添加详细任务列表
        info.append("**详细任务列表**：\n");
        List<TodoTask> tasks = todoList.getTasks();
        
        if (tasks.isEmpty()) {
            info.append("暂无任务");
        } else {
            for (int i = 0; i < tasks.size(); i++) {
                TodoTask task = tasks.get(i);
                info.append(String.format("%d. **任务ID**: %s\n", i + 1, task.getUniqueId()));
                info.append(String.format("   **描述**: %s\n", task.getDescription()));
                info.append(String.format("   **分配智能体**: %s\n", task.getAssignedAgent()));
                info.append(String.format("   **状态**: %s\n", task.getStatus()));
                info.append(String.format("   **顺序**: %d\n", task.getOrder()));
                if (task.getFailureCount() > 0) {
                    info.append(String.format("   **失败次数**: %d\n", task.getFailureCount()));
                }
                info.append("\n");
            }
        }
        
        return info.toString();
    }
    
    /**
     * 格式化子图结果
     */
    private String formatSubgraphResults(Map<String, String> subgraphResults) {
        if (subgraphResults.isEmpty()) {
            return "暂无子图执行结果";
        }
        
        StringBuilder info = new StringBuilder();
        subgraphResults.forEach((agent, result) -> 
            info.append(String.format("**%s执行结果**：\n%s\n\n", agent, result))
        );
        return info.toString();
    }
}
