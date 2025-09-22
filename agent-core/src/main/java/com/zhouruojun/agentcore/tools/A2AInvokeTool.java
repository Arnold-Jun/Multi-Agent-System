package com.zhouruojun.agentcore.tools;

import com.zhouruojun.agentcore.a2a.A2AInvocationResult;
import com.zhouruojun.agentcore.a2a.A2aClientManager;
import com.zhouruojun.agentcore.config.AgentConstants;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * A2A调用工具
 * 允许SupervisorAgent调用其他智能体
 */
@Component
@Slf4j
public class A2AInvokeTool {

    @Autowired
    private A2aClientManager a2aClientManager;

    @Tool("调用数据分析智能体进行数据分析任务")
    public String invokeDataAnalysisAgent(String task, String filePath, String sessionId) {
        try {
            log.info("Invoking data analysis agent for task: {}", task);

            Map<String, Object> parameters = new HashMap<>();
            parameters.put("message", task);
            if (filePath != null && !filePath.isEmpty()) {
                parameters.put("filePath", filePath);
            }

            A2AInvocationResult result = a2aClientManager.invokeAgent(AgentConstants.DATA_ANALYSIS_AGENT_NAME, AgentConstants.TASK_TYPE_DATA_ANALYSIS, parameters, sessionId);
            return result.success() ? result.response() : "调用数据分析智能体失败: " + result.error();
        } catch (Exception e) {
            log.error("Error invoking data analysis agent", e);
            return "调用数据分析智能体失败: " + e.getMessage();
        }
    }

    @Tool("加载数据文件到数据分析智能体")
    public String loadDataForAnalysis(String filePath, String format, String sessionId) {
        try {
            log.info("Loading data file for analysis: {}", filePath);

            Map<String, Object> parameters = new HashMap<>();
            parameters.put("filePath", filePath);
            parameters.put("format", format != null ? format : "csv");
            parameters.put("hasHeader", true);

            A2AInvocationResult result = a2aClientManager.invokeAgent(AgentConstants.DATA_ANALYSIS_AGENT_NAME, AgentConstants.TASK_TYPE_LOAD_DATA, parameters, sessionId);
            return result.success() ? result.response() : "加载数据文件失败: " + result.error();
        } catch (Exception e) {
            log.error("Error loading data for analysis", e);
            return "加载数据文件失败: " + e.getMessage();
        }
    }

    @Tool("请求数据分析智能体生成图表")
    public String generateChart(String chartType, String title, String dataDescription, String sessionId) {
        try {
            log.info("Requesting chart generation: {} - {}", chartType, title);

            Map<String, Object> parameters = new HashMap<>();
            parameters.put("chartType", chartType);
            parameters.put("title", title);
            parameters.put("message", "请根据以下描述生成图表: " + dataDescription);

            A2AInvocationResult result = a2aClientManager.invokeAgent(AgentConstants.DATA_ANALYSIS_AGENT_NAME, AgentConstants.TASK_TYPE_GENERATE_CHART, parameters, sessionId);
            return result.success() ? result.response() : "生成图表失败: " + result.error();
        } catch (Exception e) {
            log.error("Error generating chart", e);
            return "生成图表失败: " + e.getMessage();
        }
    }

    @Tool("与数据分析智能体对话")
    public String chatWithDataAnalysisAgent(String message, String sessionId) {
        try {
            log.info("Chatting with data analysis agent: {}", message);

            Map<String, Object> parameters = new HashMap<>();
            parameters.put("message", message);

            A2AInvocationResult result = a2aClientManager.invokeAgent(AgentConstants.DATA_ANALYSIS_AGENT_NAME, AgentConstants.TASK_TYPE_CHAT, parameters, sessionId);
            return result.success() ? result.response() : "与数据分析智能体对话失败: " + result.error();
        } catch (Exception e) {
            log.error("Error chatting with data analysis agent", e);
            return "与数据分析智能体对话失败: " + e.getMessage();
        }
    }

    @Tool("检查数据分析智能体状态")
    public String checkDataAnalysisAgentStatus() {
        try {
            boolean isHealthy = a2aClientManager.checkAgentHealth(AgentConstants.DATA_ANALYSIS_AGENT_NAME);
            if (isHealthy) {
                String info = a2aClientManager.getAgentInfo(AgentConstants.DATA_ANALYSIS_AGENT_NAME);
                return "数据分析智能体状态正常\n智能体信息: " + info;
            } else {
                return "数据分析智能体不可用或状态异常";
            }
        } catch (Exception e) {
            log.error("Error checking data analysis agent status", e);
            return "检查数据分析智能体状态失败: " + e.getMessage();
        }
    }
}