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
import java.util.Map;

/**
 * Planner智能体提示词提供者
 * 负责规划阶段的提示词管理
 */
@Slf4j
@Component
public class PlannerPromptProvider extends BasePromptProvider implements MainGraphPromptProvider {
    
    private static final String INITIAL_SCENARIO = "INITIAL";
    private static final String REPLAN_SCENARIO = "REPLAN";
    private static final String JSON_PARSE_ERROR_SCENARIO = "JSON_PARSE_ERROR";
    
    public PlannerPromptProvider() {
        super("planner", List.of(INITIAL_SCENARIO, REPLAN_SCENARIO, JSON_PARSE_ERROR_SCENARIO));
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
            case JSON_PARSE_ERROR_SCENARIO -> buildJsonParseErrorUserPrompt(state);
            default -> throw new IllegalArgumentException("Unsupported scenario: " + scenario);
        };
    }
    
    /**
     * 判断执行场景
     */
    private String determineScenario(MainGraphState state) {
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
        return """
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
    }
    
    /**
     * 构建重新规划系统提示词
     */
    private String buildReplanSystemPrompt() {
        return """
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
    }
    
    /**
     * 构建JSON解析错误系统提示词
     */
    private String buildJsonParseErrorSystemPrompt() {
        return """
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
    }
    
    /**
     * 构建初始规划用户提示词
     */
    private String buildInitialUserPrompt(MainGraphState state) {
        String userQuery = state.getOriginalUserQuery().orElse("请帮我制定旅游计划");
        
        return String.format("""
        **用户查询**：%s

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
        String userQuery = state.getOriginalUserQuery().orElse("用户查询");
        String todoListInfo = state.getTodoListStatistics();
        Map<String, String> subgraphResults = state.getSubgraphResults().orElse(Map.of());
        
        String subgraphInfo = formatSubgraphResults(subgraphResults);
        
        return String.format("""
        **用户查询**：%s

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
        """, userQuery, todoListInfo, subgraphInfo);
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
