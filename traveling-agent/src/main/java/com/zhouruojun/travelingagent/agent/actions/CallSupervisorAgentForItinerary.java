package com.zhouruojun.travelingagent.agent.actions;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.JSONObject;
import com.zhouruojun.travelingagent.agent.BaseAgent;
import com.zhouruojun.travelingagent.agent.dto.SupervisorResponse;
import com.zhouruojun.travelingagent.agent.state.SubgraphState;
import com.zhouruojun.travelingagent.prompts.PromptManager;
import dev.langchain4j.data.message.AiMessage;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 监督智能体调用节点（用于ItineraryPlanner子图）
 * 专门用于审查plannerAgent生成的方案，检查时间冲突、不一致或不合理的情况
 * 要求Supervisor输出JSON格式：{"next": "Finish"|"revise", "output": "修改意见或审查结果"}
 */
@Slf4j
public class CallSupervisorAgentForItinerary extends CallSubAgent {

    private static final int MAX_ITERATIONS = 5;
    private static final String NEXT_REVISE = "revise";
    private static final String NEXT_FINISH = "Finish";

    /**
     * 构造函数
     *
     * @param agentName 智能体名称
     * @param agent 智能体实例
     * @param promptManager 提示词管理器
     */
    public CallSupervisorAgentForItinerary(@NonNull String agentName, @NonNull BaseAgent agent, @NonNull PromptManager promptManager) {
        super(agentName, agent, promptManager);
    }

    @Override
    protected Map<String, Object> processResponse(AiMessage originalMessage, AiMessage filteredMessage, SubgraphState state) {
        String responseText = filteredMessage.text();
        if (responseText == null || responseText.trim().isEmpty()) {
            log.error("监督智能体 {} 返回空响应", agentName);
            return createErrorResult(state, "监督智能体返回空响应");
        }

        log.info("监督智能体 {} 审查完成，开始解析JSON响应", agentName);

        // 检查是否达到最大迭代次数
        if (state.isMaxIterationsReached()) {
            log.warn("已达到最大迭代次数 {}，强制结束", MAX_ITERATIONS);
            Map<String, Object> result = new HashMap<>();
            result.put("messages", List.of(filteredMessage));
            result.put("next", "END");
            // 直接返回完整方案作为finalResponse
            String finalPlan = state.getCurrentPlan()
                    .orElseGet(() -> state.lastMessage()
                            .filter(msg -> msg instanceof AiMessage)
                            .map(msg -> ((AiMessage) msg).text())
                            .orElse("已达到最大迭代次数，当前方案如下"));
            result.put("finalResponse", finalPlan);
            return result;
        }

        try {
            // 解析JSON响应
            SupervisorResponse supervisorResponse = parseSupervisorResponse(responseText);
            
            Map<String, Object> result = new HashMap<>();
            result.put("messages", List.of(filteredMessage));
            
            // 根据next字段决定下一步
            String next = supervisorResponse.getNext();
            if (NEXT_FINISH.equals(next)) {
                // 审查通过，结束子图
                log.info("监督智能体审查通过，方案合格");
                result.put("next", "END");
                // 直接返回完整方案作为finalResponse
                String finalPlan = state.getCurrentPlan()
                        .orElseGet(() -> state.lastMessage()
                                .filter(msg -> msg instanceof AiMessage)
                                .map(msg -> ((AiMessage) msg).text())
                                .orElse("方案已审查通过"));
                result.put("finalResponse", finalPlan);
            } else if (NEXT_REVISE.equals(next)) {
                // 需要修改，返回itineraryPlanner
                log.info("监督智能体发现需要修改的问题，返回itineraryPlanner进行修改");
                log.info("修改意见: {}", supervisorResponse.getOutput());
                
                result.put("next", "itineraryPlanner");
                // 保存修改意见到supervisorFeedback，供Planner参考
                result.put("supervisorFeedback", supervisorResponse.getOutput());
                result.put("needsRevision", true);
                
                // 增加迭代次数：通过返回Map更新状态
                int currentCount = state.getIterationCount();
                result.put("iterationCount", currentCount + 1);
            } else {
                // 无效的next值，默认结束
                log.warn("无效的next值: {}，默认结束子图", next);
                result.put("next", "END");
                String finalPlan = state.getCurrentPlan()
                        .orElseGet(() -> "审查完成");
                result.put("finalResponse", finalPlan);
            }
            
            return result;
            
        } catch (IllegalArgumentException e) {
            // JSON解析失败，返回错误
            log.error("监督智能体JSON解析失败: {}", e.getMessage());
            return createErrorResult(state, "监督智能体返回格式错误，必须是JSON格式: " + e.getMessage());
        } catch (Exception e) {
            log.error("监督智能体处理响应失败: {}", e.getMessage(), e);
            return createErrorResult(state, "监督智能体处理失败: " + e.getMessage());
        }
    }

    /**
     * 解析Supervisor的JSON响应
     * 格式: {"next": "Finish"|"revise", "output": "修改意见或审查结果"}
     */
    private SupervisorResponse parseSupervisorResponse(String responseText) {
        try {
            log.debug("解析Supervisor响应: {}", responseText);
            
            // 提取JSON部分
            String jsonStr = extractJsonFromResponse(responseText);
            if (jsonStr == null) {
                throw new IllegalArgumentException("响应中未找到有效的JSON");
            }
            
            // 解析JSON
            JSONObject json = JSON.parseObject(jsonStr);
            
            // 验证必需字段
            String next = json.getString("next");
            if (next == null || next.trim().isEmpty()) {
                throw new IllegalArgumentException("JSON响应中缺少next字段");
            }
            
            String trimmedNext = next.trim();
            if (!NEXT_FINISH.equals(trimmedNext) && !NEXT_REVISE.equals(trimmedNext)) {
                throw new IllegalArgumentException(
                    String.format("无效的next字段值: %s，有效值为: %s, %s", trimmedNext, NEXT_FINISH, NEXT_REVISE)
                );
            }
            
            String output = json.getString("output");
            // output字段在next为"revise"时必须存在
            if (NEXT_REVISE.equals(trimmedNext) && (output == null || output.trim().isEmpty())) {
                throw new IllegalArgumentException("当next为\"revise\"时，output字段不能为空");
            }
            
            SupervisorResponse response = new SupervisorResponse();
            response.setNext(trimmedNext);
            response.setOutput(output != null ? output.trim() : "");
            
            log.debug("解析Supervisor响应成功: next={}, output长度={}", 
                    response.getNext(), response.getOutput() != null ? response.getOutput().length() : 0);
            
            return response;
            
        } catch (JSONException e) {
            log.error("JSON解析错误: {}", e.getMessage());
            throw new IllegalArgumentException("无效的JSON格式: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("解析Supervisor响应失败", e);
            throw new RuntimeException("解析Supervisor响应失败: " + e.getMessage(), e);
        }
    }

    /**
     * 从响应中提取JSON字符串
     */
    private String extractJsonFromResponse(String response) {
        if (response == null || response.trim().isEmpty()) {
            return null;
        }
        
        // 尝试直接解析整个响应
        try {
            JSON.parseObject(response);
            return response;
        } catch (JSONException e) {
            // 如果直接解析失败，尝试提取JSON部分
        }
        
        // 使用正则表达式提取JSON
        Pattern jsonPattern = Pattern.compile("\\{.*\\}", Pattern.DOTALL);
        Matcher matcher = jsonPattern.matcher(response);
        
        if (matcher.find()) {
            String jsonStr = matcher.group();
            try {
                JSON.parseObject(jsonStr);
                return jsonStr;
            } catch (JSONException e) {
                log.warn("提取的字符串不是有效的JSON: {}", jsonStr);
            }
        }
        
        return null;
    }
}
