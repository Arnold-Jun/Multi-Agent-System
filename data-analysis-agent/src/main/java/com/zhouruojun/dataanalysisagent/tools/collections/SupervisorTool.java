package com.zhouruojun.dataanalysisagent.tools.collections;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.zhouruojun.dataanalysisagent.agent.task.RouteResponse;
import com.zhouruojun.dataanalysisagent.agent.task.TaskDescription;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 主管路由工具，提供结构化助手，使主管智能体能够以可预测的JSON格式将工作转发给专业子智能体
 */
@Component
@Slf4j
public class SupervisorTool {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
    private static final String DATA_ANALYSIS_AGENT = "data-analysis-agent";

    @Tool("将任务路由到数据加载子智能体")
    public static String callDataLoadingAgent(
            @P("需要加载的数据的高级描述") String query,
            @P("加载数据的目的") String purpose,
            @P("可选上下文，如文件路径、格式或凭据") String context) {
        return buildRouteSafely("data_loading", query, purpose, context,
                "调用数据加载智能体时出错: %s");
    }

    @Tool("将任务路由到统计分析子智能体")
    public static String callStatisticalAnalysisAgent(
            @P("需要回答的分析问题") String query,
            @P("统计分析的目标") String purpose,
            @P("附加上下文，如感兴趣的列或约束条件") String context) {
        return buildRouteSafely("statistical_analysis", query, purpose, context,
                "调用统计分析智能体时出错: %s");
    }

    @Tool("将任务路由到数据可视化子智能体")
    public static String callDataVisualizationAgent(
            @P("可视化意图（需要可视化什么）") String query,
            @P("图表的目的或预期洞察") String purpose,
            @P("图表偏好、目标受众、显示约束") String context) {
        return buildRouteSafely("data_visualization", query, purpose, context,
                "调用数据可视化智能体时出错: %s");
    }

    @Tool("将任务路由到网络搜索子智能体")
    public static String callWebSearchAgent(
            @P("搜索查询或关键词") String query,
            @P("搜索的目的") String purpose,
            @P("范围或约束，如时间范围或目标网站") String context) {
        return buildRouteSafely("web_search", query, purpose, context,
                "调用网络搜索智能体时出错: %s");
    }

    private static String buildRouteSafely(String action,
                                           String query,
                                           String purpose,
                                           String context,
                                           String errorTemplate) {
        try {
            log.info("将任务路由到 {} 子智能体，查询: {}", action, query);
            TaskDescription task = buildTaskDescription(query, purpose, context);
            RouteResponse response = new RouteResponse(action, DATA_ANALYSIS_AGENT, task, null);
            return OBJECT_MAPPER.writeValueAsString(response);
        } catch (Exception e) {
            log.error("为操作 {} 构建路由响应时出错: {}", action, e.getMessage(), e);
            return String.format(errorTemplate, e.getMessage());
        }
    }

    private static TaskDescription buildTaskDescription(String query, String purpose, String context) {
        if (context == null || context.isBlank()) {
            return TaskDescription.of(query, purpose);
        }
        return TaskDescription.of(query, purpose, context);
    }
}