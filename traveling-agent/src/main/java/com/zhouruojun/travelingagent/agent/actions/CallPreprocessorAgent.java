package com.zhouruojun.travelingagent.agent.actions;

import com.alibaba.fastjson.JSONObject;
import com.zhouruojun.travelingagent.agent.BaseAgent;
import com.zhouruojun.travelingagent.agent.dto.PreprocessorResponse;
import com.zhouruojun.travelingagent.agent.state.MainGraphState;
import com.zhouruojun.travelingagent.prompts.PromptManager;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 预处理器智能体调用节点
 * 负责处理简单对话和任务分类，决定是否进入完整规划流程
 * 
 * 主要功能：
 * 1. 处理简单查询（天气、酒店、机票、帖子搜索等）
 * 2. 与用户进行多轮对话收集信息
 * 3. 判断任务复杂度并路由到相应节点
 * 4. 输出JSON格式的路由决策
 */
@Slf4j
public class CallPreprocessorAgent extends CallAgent<MainGraphState> {

    private final PromptManager promptManager;

    /**
     * 构造函数
     *
     * @param agentName 智能体名称
     * @param agent 智能体实例
     * @param promptManager 提示词管理器
     */
    public CallPreprocessorAgent(@NonNull String agentName, @NonNull BaseAgent agent, @NonNull PromptManager promptManager) {
        super(agentName, agent);
        this.promptManager = promptManager;
    }

    @Override
    public Map<String, Object> apply(MainGraphState state) {
        // 检查是否有完整的结构化意图（表单提交后）
        // 如果有，直接路由到planner，跳过LLM调用
        if (state.hasSufficientIntentForPlanner()) {
            
            java.util.Optional<com.zhouruojun.travelingagent.agent.dto.TravelIntentResult> structuredIntent = state.getStructuredIntent();
            if (structuredIntent.isPresent()) {
                // 构造预处理器响应
                PreprocessorResponse response = new PreprocessorResponse();
                response.setNext("planner");
                response.setOutput(structuredIntent.get().toPlannerTaskDescription());
                
                // 更新状态
                Map<String, Object> result = updateStateWithResponse(response, state);
                result.put("preprocessorRetryContext", null);
                result.put("preprocessorRetryCount", 0);
                
                return result;
            }
        }
        
        // 正常流程：调用父类的apply方法
        return super.apply(state);
    }

    @Override
    protected List<ChatMessage> buildMessages(MainGraphState state) {
        // 使用新的提示词管理器构建消息
        List<ChatMessage> messages = promptManager.buildMainGraphMessages(agentName, state);
                
        return messages;
    }

    @Override
    protected Map<String, Object> processResponse(AiMessage originalMessage, AiMessage filteredMessage, MainGraphState state) {
        // 检查是否有工具调用请求
        if (originalMessage.hasToolExecutionRequests()) {
            log.info("预处理器智能体 {} 请求工具执行: {}", agentName, originalMessage.toolExecutionRequests());
            Map<String, Object> result = new HashMap<>();
            result.put("toolExecutionRequests", originalMessage.toolExecutionRequests());
            result.put("next", "action");
            // 工具调用路径清理重试上下文
            result.put("preprocessorRetryContext", null);
            result.put("preprocessorRetryCount", 0);
            // 为了不将错误输出持久进历史，工具调用时不强制加入filteredMessage
            result.put("messages", List.of(filteredMessage));
            return result;
        }

        // 解析预处理器响应
        String responseText = filteredMessage.text();
        if (responseText == null || responseText.trim().isEmpty()) {
            log.error("预处理器智能体 {} 返回空响应", agentName);
            return createErrorResult(state, "预处理器智能体返回空响应");
        }

        // 尝试解析为JSON；失败则进入受控重试
        try {
            PreprocessorResponse response = parsePreprocessorResponse(responseText);

            // 成功解析后，清理重试上下文
            Map<String, Object> result = updateStateWithResponse(response, state);
            result.put("preprocessorRetryContext", null);
            result.put("preprocessorRetryCount", 0);
            // 添加消息（这次为正常模型回复，可计入历史）
            result.put("messages", List.of(filteredMessage));
            return result;
        } catch (IllegalArgumentException parseEx) {
            // 可由LLM自行修复的错误（解析失败/字段缺失/无效next等）统一走重试链路
            return buildRetryResult(state, responseText, parseEx.getMessage());
        } catch (Exception otherEx) {
            // 其他非预期异常：直接返回错误给前端
            log.error("预处理器智能体 {} 遇到非预期异常: {}", agentName, otherEx.getMessage(), otherEx);
            return createErrorResult(state, "预处理器处理失败: " + otherEx.getMessage());
        }
    }
    
    /**
     * 解析预处理器响应
     */
    private PreprocessorResponse parsePreprocessorResponse(String responseText) {
        try {
            // 尝试解析JSON
            JSONObject jsonResponse = JSONObject.parseObject(responseText);
            
            String next = jsonResponse.getString("next");
            String output = jsonResponse.getString("output");
            
            if (next == null || next.trim().isEmpty()) {
                throw new IllegalArgumentException("JSON响应中缺少next字段");
            }
            if (output == null || output.trim().isEmpty()) {
                throw new IllegalArgumentException("JSON响应中缺少output字段");
            }
            
            // 验证next字段值是否有效
            String trimmedNext = next.trim();
            if (!isValidNextValue(trimmedNext)) {
                throw new IllegalArgumentException("无效的next字段值: " + trimmedNext + "，有效值为: planner, Finish, action, userInput");
            }
            
            PreprocessorResponse response = new PreprocessorResponse();
            response.setNext(trimmedNext);
            response.setOutput(output.trim());
            
            return response;
            
        } catch (Exception e) {
            log.error("解析预处理器JSON响应失败: {}", e.getMessage());
            throw new IllegalArgumentException("预处理器响应格式错误，必须是包含next和output字段的JSON", e);
        }
    }
    
    /**
     * 验证next字段值是否有效
     */
    private boolean isValidNextValue(String nextValue) {
        return "planner".equals(nextValue) || "Finish".equals(nextValue) || "action".equals(nextValue) || "formInput".equals(nextValue);
    }

    /**
     * 构建重试结果。
     */
    private Map<String, Object> buildRetryResult(MainGraphState state, String responseText, String errorMessage) {
        int retryCount = 0;
        try {
            Object rc = state.getValue("preprocessorRetryCount").orElse(0);
            if (rc instanceof Integer) retryCount = (Integer) rc; else retryCount = Integer.parseInt(rc.toString());
        } catch (Exception ignored) { retryCount = 0; }

        if (retryCount >= 2) {
            String finalMsg = "抱歉，我多次未能生成有效的JSON结果。请稍后重试，或换一种表达方式。";
            log.warn("preprocessor JSON解析连续失败，已达上限: {}", retryCount);
            return Map.of(
                    "finalResponse", finalMsg,
                    "next", "Finish",
                    "preprocessorRetryContext", null,
                    "preprocessorRetryCount", 0
            );
        }

        String truncatedOutput = responseText != null && responseText.length() > 500
                ? responseText.substring(0, 500) + "...（已截断）" : (responseText == null ? "" : responseText);
        String retryContext = "【上一次输出无法解析为JSON】\n"
                + "请严格输出仅包含next与output两个字段的JSON，禁止任何额外文本。\n"
                + "示例：{\"next\":\"planner|Finish\",\"output\":\"...\"}\n"
                + "错误信息：" + (errorMessage == null ? "" : errorMessage) + "\n"
                + "原始输出片段：\n" + truncatedOutput + "\n";

        Map<String, Object> retryResult = new HashMap<>();
        retryResult.put("preprocessorRetryContext", retryContext);
        retryResult.put("preprocessorRetryCount", retryCount + 1);
        retryResult.put("next", "retry");
        return retryResult;
    }
    
    /**
     * 根据响应更新状态
     */
    private Map<String, Object> updateStateWithResponse(PreprocessorResponse response, MainGraphState state) {
        Map<String, Object> result = new HashMap<>();
        // userQueryHistory 完全由 ControllerCore 维护，这里不做任何修改
        
        // 更新preprocessor响应历史
        List<String> preprocessorResponseHistory = new ArrayList<>(state.getPreprocessorResponseHistory());
        preprocessorResponseHistory.add(response.getOutput());
        result.put("preprocessorResponseHistory", preprocessorResponseHistory);
        
        // 根据next字段处理不同路由
        switch (response.getNext()) {
            case "Finish":
                // 直接返回结果给用户
                result.put("finalResponse", response.getOutput());
                result.put("next", "Finish");
                break;
                
            case "planner":
                // 进入完整规划流程
                List<String> taskHistory = new ArrayList<>(state.getTaskHistory());
                // 如果有结构化意图，优先使用结构化意图的任务描述
                java.util.Optional<com.zhouruojun.travelingagent.agent.dto.TravelIntentResult> structuredIntent = state.getStructuredIntent();
                if (structuredIntent.isPresent() && structuredIntent.get().isComplete()) {
                    taskHistory.add(structuredIntent.get().toPlannerTaskDescription());
                } else {
                    taskHistory.add(response.getOutput());
                }
                result.put("taskHistory", taskHistory);
                result.put("next", "planner");
                break;
                
            case "formInput":
                // 触发表单收集
                result.put("finalResponse", response.getOutput());
                result.put("formInputRequired", true);
                result.put("next", "formInput"); // 路由到formInput节点等待表单提交
                // 构建表单schema和默认值
                Map<String, Object> formSchema = buildFormSchema(state);
                result.put("formSchema", formSchema);
                break;
                
            default:
                throw new IllegalArgumentException("无效的next字段值: " + response.getNext());
        }
        
        return result;
    }
    

    /**
     * 构建表单schema和默认值
     */
    private Map<String, Object> buildFormSchema(MainGraphState state) {
        Map<String, Object> schema = new HashMap<>();
        
        // 基础schema定义
        Map<String, Object> fields = new HashMap<>();
        
        // 目的地字段
        Map<String, Object> destinationField = new HashMap<>();
        destinationField.put("type", "string");
        destinationField.put("title", "目的地");
        destinationField.put("required", true);
        destinationField.put("placeholder", "例如：云南 昆明-大理-丽江");
        fields.put("destination", destinationField);
        
        // 出发日期字段
        Map<String, Object> startDateField = new HashMap<>();
        startDateField.put("type", "string");
        startDateField.put("format", "date");
        startDateField.put("title", "出发日期");
        startDateField.put("required", false);
        fields.put("startDate", startDateField);
        
        // 旅行天数字段
        Map<String, Object> daysField = new HashMap<>();
        daysField.put("type", "integer");
        daysField.put("title", "旅行天数");
        daysField.put("min", 1);
        daysField.put("max", 30);
        daysField.put("required", false);
        fields.put("days", daysField);
        
        // 人数字段
        Map<String, Object> peopleCountField = new HashMap<>();
        peopleCountField.put("type", "integer");
        peopleCountField.put("title", "人数");
        peopleCountField.put("min", 1);
        peopleCountField.put("max", 20);
        peopleCountField.put("required", true);
        fields.put("peopleCount", peopleCountField);
        
        // 预算档位字段
        Map<String, Object> budgetRangeField = new HashMap<>();
        budgetRangeField.put("type", "string");
        budgetRangeField.put("title", "预算档位");
        budgetRangeField.put("enum", java.util.List.of("economy", "standard", "premium"));
        budgetRangeField.put("enumLabels", java.util.List.of("经济型（人均1000-2000元）", "适中型（人均2000-5000元）", "高端型（人均5000元以上）"));
        budgetRangeField.put("required", false);
        fields.put("budgetRange", budgetRangeField);
        
        // 预算金额字段
        Map<String, Object> budgetAmountField = new HashMap<>();
        budgetAmountField.put("type", "number");
        budgetAmountField.put("title", "预算金额（元）");
        budgetAmountField.put("required", false);
        fields.put("budgetAmount", budgetAmountField);
        
        // 偏好字段
        Map<String, Object> preferencesField = new HashMap<>();
        preferencesField.put("type", "array");
        preferencesField.put("title", "偏好");
        preferencesField.put("items", Map.of("type", "string"));
        preferencesField.put("enum", java.util.List.of("history", "food", "outdoor", "shopping", "family", "relax"));
        preferencesField.put("enumLabels", java.util.List.of("历史文化", "美食", "户外运动", "购物", "亲子", "休闲"));
        preferencesField.put("required", false);
        fields.put("preferences", preferencesField);
        
        // 住宿标准字段
        Map<String, Object> lodgingLevelField = new HashMap<>();
        lodgingLevelField.put("type", "string");
        lodgingLevelField.put("title", "住宿标准");
        lodgingLevelField.put("enum", java.util.List.of("hostel", "budget", "comfort", "luxury"));
        lodgingLevelField.put("enumLabels", java.util.List.of("青旅", "经济型", "舒适型", "高端型"));
        lodgingLevelField.put("required", false);
        fields.put("lodgingLevel", lodgingLevelField);
        
        // 交通偏好字段
        Map<String, Object> transportPreferenceField = new HashMap<>();
        transportPreferenceField.put("type", "string");
        transportPreferenceField.put("title", "交通偏好");
        transportPreferenceField.put("enum", java.util.List.of("train", "flight", "self-drive", "none"));
        transportPreferenceField.put("enumLabels", java.util.List.of("高铁", "飞机", "自驾", "无偏好"));
        transportPreferenceField.put("required", false);
        fields.put("transportPreference", transportPreferenceField);
        
        // 备注字段
        Map<String, Object> notesField = new HashMap<>();
        notesField.put("type", "string");
        notesField.put("title", "备注");
        notesField.put("maxLength", 500);
        notesField.put("required", false);
        fields.put("notes", notesField);
        
        schema.put("fields", fields);
        
        // 设置默认值
        Map<String, Object> defaults = new HashMap<>();
        defaults.put("peopleCount", 2); // 默认2人
        defaults.put("budgetRange", "standard"); // 默认适中档位
        
        schema.put("defaults", defaults);
        
        return schema;
    }

    /**
     * 创建错误结果
     */
    protected Map<String, Object> createErrorResult(MainGraphState state, String errorMessage) {
        log.error("预处理器智能体错误: {}", errorMessage);
        
        // 创建包含错误信息的AiMessage
        AiMessage errorAiMessage = AiMessage.from(errorMessage);
        
        return Map.of(
                "messages", List.of(errorAiMessage),
                "finalResponse", errorMessage,
                "next", "Finish"
        );
    }
}
