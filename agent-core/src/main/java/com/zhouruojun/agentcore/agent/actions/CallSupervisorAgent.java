package com.zhouruojun.agentcore.agent.actions;

import com.zhouruojun.agentcore.agent.SupervisorAgent;
import com.zhouruojun.agentcore.agent.state.AgentMessageState;
import com.zhouruojun.agentcore.common.ContentFilter;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.extern.slf4j.Slf4j;
import org.bsc.async.AsyncGenerator;
import org.bsc.langgraph4j.action.NodeAction;
import org.bsc.langgraph4j.langchain4j.generators.StreamingChatGenerator;
import org.bsc.langgraph4j.streaming.StreamingOutput;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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


    private Map<String, Object> mapResult(ChatResponse response, AgentMessageState state) {
        var content = response.aiMessage();
        String responseText = content.text();
        log.info("SupervisorAgent raw response: {}", responseText);

        String filteredResponse = ContentFilter.filterThinkingContent(responseText);
        
        // 解析JSON格式的响应
        return parseJsonResponse(filteredResponse);
    }
    
    /**
     * 解析JSON格式的supervisor响应
     */
    private Map<String, Object> parseJsonResponse(String responseText) {
        // 尝试提取JSON部分
        String jsonText = extractJsonFromResponse(responseText);
        
        // 解析JSON
        com.alibaba.fastjson.JSONObject jsonResponse = com.alibaba.fastjson.JSON.parseObject(jsonText);
        
        String next = jsonResponse.getString("next");
        String message = jsonResponse.getString("message");
        
        if (next == null || message == null) {
            throw new IllegalArgumentException("Invalid JSON response: missing 'next' or 'message' field");
        }
        
        log.info("Parsed JSON response - next: {}, message: {}", next, message);
        
        if ("agentInvoke".equals(next)) {
            // 从JSON中获取目标智能体
            String targetAgent = jsonResponse.getString("targetAgent");
            
            if (targetAgent == null || targetAgent.trim().isEmpty()) {
                throw new IllegalArgumentException("Invalid JSON response: missing 'targetAgent' field for agentInvoke");
            }
            
            log.info("CallSupervisorAgent using targetAgent from JSON: {}, taskInstruction: {}", targetAgent, message);
            
            String userFriendlyMessage = generateAgentInvokeMessage(targetAgent, message);
            
            return Map.of(
                    "next", "agentInvoke",
                    "nextAgent", targetAgent,
                    "taskInstruction", message,  // 使用message作为任务指令
                    "agent_response", userFriendlyMessage,  // 使用用户友好的消息
                    "username", username
            );
        } else if ("FINISH".equals(next)) {
            return Map.of("next", "FINISH", "agent_response", message);
        } else {
            throw new IllegalArgumentException("Invalid 'next' value: " + next);
        }
    }
    
    /**
     * 从响应中提取JSON部分
     */
    private String extractJsonFromResponse(String responseText) {
        // 查找JSON对象的开始和结束
        int startIndex = responseText.indexOf('{');
        int endIndex = responseText.lastIndexOf('}');
        
        if (startIndex == -1 || endIndex == -1 || startIndex >= endIndex) {
            throw new IllegalArgumentException("No valid JSON found in response");
        }
        
        return responseText.substring(startIndex, endIndex + 1);
    }
    

    /**
     * 生成用户友好的提示消息
     */
    private String generateAgentInvokeMessage(String targetAgent, String taskInstruction) {
        String agentDisplayName = switch (targetAgent) {
            case "job-search-agent" -> "求职智能体";
            case "data-analysis-agent" -> "数据分析智能体";
            default -> "专业智能体";
        };
        
        return String.format("正在为您调用%s处理您的请求，请稍候...", agentDisplayName);
    }
    
    

}