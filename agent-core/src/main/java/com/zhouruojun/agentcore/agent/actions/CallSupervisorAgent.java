package com.zhouruojun.agentcore.agent.actions;

import com.zhouruojun.agentcore.agent.SupervisorAgent;
import com.zhouruojun.agentcore.agent.state.AgentMessageState;
import com.zhouruojun.agentcore.common.ContentFilter;
import com.zhouruojun.agentcore.config.AgentConstants;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.extern.slf4j.Slf4j;
import org.bsc.async.AsyncGenerator;
import org.bsc.langgraph4j.action.NodeAction;
import org.bsc.langgraph4j.langchain4j.generators.StreamingChatGenerator;
import org.bsc.langgraph4j.streaming.StreamingOutput;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;

/**
 * 调用主管智能体节点
 * 负责调用SupervisorAgent并处理其响应
 */
@Slf4j
public class CallSupervisorAgent implements NodeAction<AgentMessageState> {


    private final SupervisorAgent agent;
    private final String agentName;
    private final String username;

    private StreamingChatGenerator<AgentMessageState> generator;
    private BlockingQueue<AsyncGenerator.Data<StreamingOutput<AgentMessageState>>> queue;

    public CallSupervisorAgent(String agentName,
                               SupervisorAgent agent,
                               String username) {
        this.agent = agent;
        this.agentName = agentName;
        this.username = username;
    }

    public void setQueue(BlockingQueue<AsyncGenerator.Data<StreamingOutput<AgentMessageState>>> queue) {
        this.queue = queue;
    }

    @Override
    public Map<String, Object> apply(AgentMessageState state) throws Exception {
        log.info("CallSupervisorAgent executing");

        List<ChatMessage> messages = new ArrayList<>(state.messages());
        if (messages.isEmpty()) {
            throw new IllegalArgumentException("no input provided!");
        }

        Optional<String> processingStage = state.processingStage();
        if (processingStage.isPresent() && "final_reasoning".equals(processingStage.get())) {
            messages = prepareFinalReasoningMessages(state, messages);
        }

        if (agent.isStreaming()) {
            if (generator == null) {
                generator = StreamingChatGenerator.<AgentMessageState>builder()
                        .queue(queue)
                        .mapResult(response -> mapResult(response, state))
                        .startingNode(agentName)
                        .startingState(state)
                        .build();
            }

            agent.execute(messages, generator.handler());
            return Map.of("_generator", generator);
        }

        var response = agent.execute(messages);
        return mapResult(response, state);
    }

    private List<ChatMessage> prepareFinalReasoningMessages(AgentMessageState state, List<ChatMessage> messages) {
        Optional<String> agentResponse = state.agentResponse();
        if (agentResponse.isEmpty()) {
            log.warn("No agent response found for final reasoning");
            return messages;
        }

        String responseText = agentResponse.get();
        List<ChatMessage> enhancedMessages = new ArrayList<>(messages);
        enhancedMessages.add(UserMessage.from(
                "专业智能体已经完成了分析，请将以下技术分析结果转化为用户友好的回复，总结关键发现并提供实用建议。不要再次调用专业智能体，直接给出最终回复：\n\n"
                        + responseText.substring(0, Math.min(500, responseText.length())) + "..."));
        return enhancedMessages;
    }

    private Map<String, Object> mapResult(ChatResponse response, AgentMessageState state) {
        var content = response.aiMessage();
        String responseText = content.text();
        log.info("SupervisorAgent raw response: {}", responseText);

        String filteredResponse = ContentFilter.filterThinkingContent(responseText);

        if (filteredResponse.contains("FINISH") || filteredResponse.contains("任务完成")) {
            return Map.of("next", "FINISH", "agent_response", filteredResponse);
        }

        String lowerText = filteredResponse.toLowerCase();
        if (lowerText.contains("需要调用专业智能体") || lowerText.contains("发送给") ||
            lowerText.contains("路由到") || lowerText.contains("调用")) {
            
            // 智能判断应该调用哪个智能体
            String targetAgent = determineTargetAgent(filteredResponse);
            String taskInstruction = extractTaskInstructionFromResponse(filteredResponse);
            
            return Map.of(
                    "next", "agentInvoke",
                    "nextAgent", targetAgent,
                    "taskInstruction", taskInstruction,
                    "agent_response", filteredResponse,
                    "username", username
            );
        }

        if (lowerText.contains("任务完成") || lowerText.contains("完成")) {
            return Map.of("next", "FINISH", "agent_response", filteredResponse);
        }

        return Map.of("next", "FINISH", "agent_response", filteredResponse);
    }

    /**
     * 智能判断应该调用哪个智能体
     */
    private String determineTargetAgent(String responseText) {
        String lowerText = responseText.toLowerCase();
        
        // 根据关键词判断智能体类型
        if (lowerText.contains("job-search-agent") || lowerText.contains("求职") || 
            lowerText.contains("岗位") || lowerText.contains("简历") || 
            lowerText.contains("面试") || lowerText.contains("投递") ||
            lowerText.contains("工作") || lowerText.contains("招聘")) {
            return AgentConstants.JOB_SEARCH_AGENT_NAME;
        }
        
        if (lowerText.contains("data-analysis-agent") || lowerText.contains("数据分析") || 
            lowerText.contains("统计") || lowerText.contains("可视化") || 
            lowerText.contains("图表") || lowerText.contains("数据")) {
            return AgentConstants.DATA_ANALYSIS_AGENT_NAME;
        }
        
        // 默认返回数据分析智能体（保持向后兼容）
        log.warn("无法确定目标智能体，使用默认的数据分析智能体");
        return AgentConstants.DATA_ANALYSIS_AGENT_NAME;
    }
    
    /**
     * 从响应中提取任务指令
     */
    private String extractTaskInstructionFromResponse(String responseText) {
        try {
            String[] lines = responseText.split("\n");
            for (String line : lines) {
                if (line.contains("具体任务指令")) {
                    int delimiterIndex = Math.max(line.indexOf('：'), line.indexOf(':'));
                    if (delimiterIndex >= 0 && delimiterIndex + 1 < line.length()) {
                        String instruction = line.substring(delimiterIndex + 1).trim();
                        if (hasText(instruction)) {
                            return instruction;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Error extracting task instruction: {}", e.getMessage());
        }

        // 如果没有找到具体任务指令，返回通用任务
        return "请根据用户需求进行处理";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

}