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
                throw new IllegalArgumentException("无效的next字段值: " + trimmedNext + "，有效值为: planner, Finish, action");
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
        return "planner".equals(nextValue) || "Finish".equals(nextValue) || "action".equals(nextValue);
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
        
        // 处理userQueryHistory的更新
        List<String> userQueryHistory = new ArrayList<>(state.getUserQueryHistory());
        
        // 检查是否是新的session（没有历史状态）
        boolean isNewSession = userQueryHistory.isEmpty();
        
        if (isNewSession) {
            // 第一次调用：将originalUserQuery添加到userQueryHistory
            //TODO
            String originalQuery = state.getOriginalUserQuery().orElse("");
            if (!originalQuery.isEmpty()) {
                userQueryHistory.add(originalQuery);
            }
        }
        // 后续调用：userQueryHistory已经在ControllerCore中更新，不需要重复添加

        result.put("userQueryHistory", userQueryHistory);
        
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
                taskHistory.add(response.getOutput());
                result.put("taskHistory", taskHistory);
                result.put("next", "planner");
                break;
                
            default:
                throw new IllegalArgumentException("无效的next字段值: " + response.getNext());
        }
        
        return result;
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
