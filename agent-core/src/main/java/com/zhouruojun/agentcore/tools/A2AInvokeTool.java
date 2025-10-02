package com.zhouruojun.agentcore.tools;

import com.zhouruojun.a2a.client.A2aClientService;
import com.zhouruojun.a2acore.spec.Message;
import com.zhouruojun.a2acore.spec.Part;
import com.zhouruojun.a2acore.spec.Role;
import com.zhouruojun.a2acore.spec.TaskSendParams;
import com.zhouruojun.a2acore.spec.TextPart;
import com.zhouruojun.a2acore.spec.message.SendTaskResponse;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A2A调用工具
 * 允许SupervisorAgent调用其他智能体
 */
@Component
@Slf4j
public class A2AInvokeTool {

    @Autowired(required = false)
    private A2aClientService a2aClientService;

    @Tool("调用数据分析智能体进行数据分析任务")
    public String invokeDataAnalysisAgent(String task, String filePath, String sessionId) {
        try {
            log.info("Invoking data analysis agent for task: {}", task);

            // 构建标准A2A任务参数
            TaskSendParams params = TaskSendParams.builder()
                .id(UUID.randomUUID().toString())
                .sessionId(sessionId)
                .message(buildMessage(task, filePath))
                .acceptedOutputModes(Arrays.asList("text", "data"))
                .build();
            
            // 使用标准A2A协议调用
            if (a2aClientService == null) {
                return "A2A客户端服务不可用";
            }
            if (a2aClientService == null) {
                return "A2A客户端服务不可用";
            }
            SendTaskResponse response = a2aClientService.invokeAgent("data-analysis-agent", params);
            
            if (response.getError() != null) {
                return "调用失败: " + response.getError().getMessage();
            }
            
            return extractResult(response.getResult());
        } catch (Exception e) {
            log.error("Error invoking data analysis agent", e);
            return "调用失败: " + e.getMessage();
        }
    }

    @Tool("加载数据文件到数据分析智能体")
    public String loadDataForAnalysis(String filePath, String format, String sessionId) {
        try {
            log.info("Loading data file for analysis: {}", filePath);

            String message = String.format("请加载数据文件: %s, 格式: %s", filePath, format != null ? format : "csv");
            TaskSendParams params = TaskSendParams.builder()
                .id(UUID.randomUUID().toString())
                .sessionId(sessionId)
                .message(buildMessage(message, filePath))
                .build();
            
            if (a2aClientService == null) {
                return "A2A客户端服务不可用";
            }
            SendTaskResponse response = a2aClientService.invokeAgent("data-analysis-agent", params);
            
            if (response.getError() != null) {
                return "加载数据文件失败: " + response.getError().getMessage();
            }
            
            return extractResult(response.getResult());
        } catch (Exception e) {
            log.error("Error loading data for analysis", e);
            return "加载数据文件失败: " + e.getMessage();
        }
    }

    @Tool("请求数据分析智能体生成图表")
    public String generateChart(String chartType, String title, String dataDescription, String sessionId) {
        try {
            log.info("Requesting chart generation: {} - {}", chartType, title);

            String message = String.format("请生成图表 - 类型: %s, 标题: %s, 描述: %s", chartType, title, dataDescription);
            TaskSendParams params = TaskSendParams.builder()
                .id(UUID.randomUUID().toString())
                .sessionId(sessionId)
                .message(buildMessage(message, null))
                .build();
            
            if (a2aClientService == null) {
                return "A2A客户端服务不可用";
            }
            SendTaskResponse response = a2aClientService.invokeAgent("data-analysis-agent", params);
            
            if (response.getError() != null) {
                return "生成图表失败: " + response.getError().getMessage();
            }
            
            return extractResult(response.getResult());
        } catch (Exception e) {
            log.error("Error generating chart", e);
            return "生成图表失败: " + e.getMessage();
        }
    }

    @Tool("与数据分析智能体对话")
    public String chatWithDataAnalysisAgent(String message, String sessionId) {
        try {
            log.info("Chatting with data analysis agent: {}", message);

            TaskSendParams params = TaskSendParams.builder()
                .id(UUID.randomUUID().toString())
                .sessionId(sessionId)
                .message(buildMessage(message, null))
                .build();
            
            if (a2aClientService == null) {
                return "A2A客户端服务不可用";
            }
            SendTaskResponse response = a2aClientService.invokeAgent("data-analysis-agent", params);
            
            if (response.getError() != null) {
                return "与数据分析智能体对话失败: " + response.getError().getMessage();
            }
            
            return extractResult(response.getResult());
        } catch (Exception e) {
            log.error("Error chatting with data analysis agent", e);
            return "与数据分析智能体对话失败: " + e.getMessage();
        }
    }

    @Tool("检查数据分析智能体状态")
    public String checkDataAnalysisAgentStatus() {
        try {
            // 尝试获取客户端来检查状态
            if (a2aClientService == null) {
                return "A2A客户端服务不可用";
            }
            var clients = a2aClientService.getAllClients();
            var dataAnalysisClient = clients.stream()
                .filter(client -> "data-analysis-agent".equals(client.getAgentCard().getName()))
                .findFirst();
            
            if (dataAnalysisClient.isPresent()) {
                return "数据分析智能体状态正常\n智能体信息: " + dataAnalysisClient.get().getAgentCard().getName();
            } else {
                return "数据分析智能体不可用或状态异常";
            }
        } catch (Exception e) {
            log.error("Error checking data analysis agent status", e);
            return "检查数据分析智能体状态失败: " + e.getMessage();
        }
    }
    
    @Tool("流式调用数据分析智能体")
    public Flux<String> invokeDataAnalysisAgentStreaming(String task, String sessionId) {
        try {
            log.info("Streaming invoke data analysis agent for task: {}", task);
            
            TaskSendParams params = TaskSendParams.builder()
                .id(UUID.randomUUID().toString())
                .sessionId(sessionId)
                .message(buildMessage(task, null))
                .build();
            
            if (a2aClientService == null) {
                return Flux.just("A2A客户端服务不可用");
            }
            return a2aClientService.invokeAgentStreaming("data-analysis-agent", params)
                .map(response -> extractResult(response));
        } catch (Exception e) {
            log.error("Error in streaming invoke", e);
            return Flux.just("流式调用失败: " + e.getMessage());
        }
    }
    
    /**
     * 构建A2A消息
     */
    private Message buildMessage(String text, String filePath) {
        TextPart textPart = new TextPart();
        textPart.setText(text);
        
        if (filePath != null && !filePath.isEmpty()) {
            // 可以添加文件路径到元数据中
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("filePath", filePath);
            
            return Message.builder()
                .role(Role.USER)
                .parts(Arrays.asList(textPart))
                .metadata(metadata)
                .build();
        } else {
            return Message.builder()
                .role(Role.USER)
                .parts(Arrays.asList(textPart))
                .build();
        }
    }
    
    /**
     * 从任务结果中提取文本内容
     */
    private String extractResult(com.zhouruojun.a2acore.spec.Task task) {
        if (task == null || task.getHistory() == null || task.getHistory().isEmpty()) {
            return "无结果返回";
        }
        
        // 获取最后一条消息
        Message lastMessage = task.getHistory().get(task.getHistory().size() - 1);
        if (lastMessage.getParts() != null && !lastMessage.getParts().isEmpty()) {
            Part part = lastMessage.getParts().get(0);
            if (part instanceof TextPart) {
                return ((TextPart) part).getText();
            }
        }
        
        return "无法提取结果内容";
    }
    
    /**
     * 从流式响应中提取结果
     */
    private String extractResult(com.zhouruojun.a2acore.spec.message.SendTaskStreamingResponse response) {
        if (response.getError() != null) {
            return "流式调用错误: " + response.getError().getMessage();
        }
        
        // 这里可以根据实际的流式响应格式来提取内容
        return "流式响应: " + response.toString();
    }
}