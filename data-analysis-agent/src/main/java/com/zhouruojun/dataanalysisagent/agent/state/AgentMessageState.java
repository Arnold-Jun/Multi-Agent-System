package com.zhouruojun.dataanalysisagent.agent.state;

import dev.langchain4j.data.message.ChatMessage;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import java.util.Map;
import java.util.Optional;

/**
 * Agent消息状态管理类
 * 扩展MessagesState以支持智能体间的复杂状态管理
 *
 */
public class AgentMessageState extends MessagesState<ChatMessage> {

    public AgentMessageState(Map<String, Object> initData) {
        super(initData);
    }

    /**
     * 获取下一个执行节点
     */
    public Optional<String> next() {
        return this.value("next");
    }

    /**
     * 获取任务ID
     */
    public Optional<String> taskId() {
        return this.value("task_id");
    }

    /**
     * 获取会话ID
     */
    public Optional<String> sessionId() {
        return this.value("sessionId");
    }

    /**
     * 获取请求ID
     */
    public Optional<String> requestId() {
        return this.value("requestId");
    }

    /**
     * 获取方法
     */
    public Optional<String> method() {
        return this.value("method");
    }

    /**
     * 获取下一个智能体
     */
    public Optional<String> nextAgent() {
        return this.value("nextAgent");
    }

    /**
     * 获取当前智能体
     */
    public Optional<String> currentAgent() {
        return this.value("currentAgent");
    }

    /**
     * 获取用户名
     */
    public Optional<String> username() {
        return this.value("username");
    }

    /**
     * 获取技能
     */
    public Optional<String> skill() {
        return this.value("skill");
    }

    /**
     * 检查是否有消息历史
     */
    public boolean hasMessages() {
        return !messages().isEmpty();
    }

    /**
     * 获取最后一条消息
     */
    public Optional<ChatMessage> lastMessage() {
        var messages = messages();
        if (messages.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(messages.get(messages.size() - 1));
    }

    // === 数据分析相关方法 ===

    /**
     * 检查数据是否准备就绪
     */
    public boolean isDataReady() {
        return this.value("dataReady").map(v -> (Boolean) v).orElse(false);
    }

    /**
     * 获取数据文件路径
     */
    public Optional<String> dataFilePath() {
        return this.value("dataFilePath");
    }

    /**
     * 获取数据格式
     */
    public Optional<String> dataFormat() {
        return this.value("dataFormat");
    }

    /**
     * 获取数据行数
     */
    public Optional<Integer> dataRows() {
        return this.value("dataRows");
    }

    /**
     * 获取数据列数
     */
    public Optional<Integer> dataColumns() {
        return this.value("dataColumns");
    }

    /**
     * 获取目标列名
     */
    public Optional<String> targetColumn() {
        return this.value("targetColumn");
    }

    /**
     * 获取统计信息
     */
    public Optional<String> statisticalInfo() {
        return this.value("statisticalInfo");
    }

    /**
     * 获取可视化结果
     */
    public Optional<String> visualizationResult() {
        return this.value("visualizationResult");
    }

    /**
     * 获取图表类型
     */
    public Optional<String> chartType() {
        return this.value("chartType");
    }

    /**
     * 获取图表标题
     */
    public Optional<String> chartTitle() {
        return this.value("chartTitle");
    }

    /**
     * 获取图表文件路径
     */
    public Optional<String> chartFilePath() {
        return this.value("chartFilePath");
    }

    /**
     * 获取错误消息
     */
    public Optional<String> errorMessage() {
        return this.value("errorMessage");
    }

    /**
     * 获取重试次数
     */
    public Optional<Integer> retryCount() {
        return this.value("retryCount");
    }

    /**
     * 获取最大重试次数
     */
    public Optional<Integer> maxRetries() {
        return this.value("maxRetries");
    }

    /**
     * 获取最终响应
     */
    public Optional<String> finalResponse() {
        return this.value("finalResponse");
    }

    /**
     * 检查是否应该重试
     */
    public boolean shouldRetry() {
        int current = retryCount().orElse(0);
        int max = maxRetries().orElse(3);
        return current < max;
    }

    /**
     * 获取进度信息
     */
    public String getProgressInfo() {
        StringBuilder progress = new StringBuilder();
        
        // 数据加载进度
        if (isDataReady()) {
            progress.append("✅ 数据已加载");
            dataRows().ifPresent(rows -> progress.append(" (").append(rows).append(" 行)"));
            progress.append("\n");
        } else {
            progress.append("⏳ 等待数据加载\n");
        }
        
        // 统计分析进度
        if (statisticalInfo().isPresent()) {
            progress.append("✅ 统计分析已完成\n");
        } else if (isDataReady()) {
            progress.append("⏳ 准备统计分析\n");
        }
        
        // 可视化进度
        if (visualizationResult().isPresent()) {
            progress.append("✅ 数据可视化已完成\n");
        } else if (statisticalInfo().isPresent()) {
            progress.append("⏳ 准备数据可视化\n");
        }
        
        // 错误信息
        errorMessage().ifPresent(error -> progress.append("❌ 错误: ").append(error).append("\n"));
        
        return progress.toString().trim();
    }
}