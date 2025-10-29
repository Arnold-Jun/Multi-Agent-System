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
        try {
            // 检查是否有工具调用请求
            if (originalMessage.hasToolExecutionRequests()) {
                log.info("预处理器智能体 {} 请求工具执行: {}", agentName, originalMessage.toolExecutionRequests());
                return Map.of(
                        "messages", List.of(filteredMessage),
                        "toolExecutionRequests", originalMessage.toolExecutionRequests(),
                        "next", "action"
                );
            }
            
            // 解析预处理器响应
            String responseText = filteredMessage.text();
            if (responseText == null || responseText.trim().isEmpty()) {
                log.error("预处理器智能体 {} 返回空响应", agentName);
                return createErrorResult(state, "预处理器智能体返回空响应");
            }
            
            // 解析JSON输出
            PreprocessorResponse response = parsePreprocessorResponse(responseText);
            
            // 更新状态
            Map<String, Object> result = updateStateWithResponse(response, state);
            
            // 添加消息
            result.put("messages", List.of(filteredMessage));
            
            
            return result;
            
        } catch (Exception e) {
            log.error("预处理器智能体 {} 处理响应失败: {}", agentName, e.getMessage(), e);
            
            // 发生错误时，创建错误消息并路由到Finish
            String errorMessage = String.format("预处理器处理失败: %s", e.getMessage());
            AiMessage errorAiMessage = AiMessage.from(errorMessage);
            
            return Map.of(
                    "messages", List.of(errorAiMessage),
                    "finalResponse", errorMessage,
                    "next", "Finish"
            );
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
            
            PreprocessorResponse response = new PreprocessorResponse();
            response.setNext(next.trim());
            response.setOutput(output.trim());
            
            return response;
            
        } catch (Exception e) {
            log.error("解析预处理器JSON响应失败: {}", e.getMessage());
            throw new IllegalArgumentException("预处理器响应格式错误，必须是包含next和output字段的JSON", e);
        }
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
