package com.zhouruojun.travelingagent.agent.state.tool;

import lombok.Data;

/**
 * 工具执行记录
 * 记录单次工具执行的详细信息
 */
@Data
public class ToolExecutionRecord {

    // 工具名称
    private String toolName;
    
    // 执行结果
    private String result;
    
    // 执行顺序
    private int executionOrder;
    
    // 时间戳
    private long timestamp;
    
    // 是否成功
    private boolean success;
    
    // 执行时长（毫秒）
    private long duration;

    /**
     * 构造函数
     */
    public ToolExecutionRecord() {
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * 构造函数
     */
    public ToolExecutionRecord(String toolName, String result, boolean success) {
        this();
        this.toolName = toolName;
        this.result = result;
        this.success = success;
    }

    /**
     * 获取执行时间（格式化）
     */
    public String getFormattedTimestamp() {
        return new java.util.Date(timestamp).toString();
    }

    /**
     * 获取执行时长（格式化）
     */
    public String getFormattedDuration() {
        if (duration < 1000) {
            return duration + "ms";
        } else if (duration < 60000) {
            return String.format("%.2fs", duration / 1000.0);
        } else {
            return String.format("%.2fmin", duration / 60000.0);
        }
    }

    /**
     * 获取状态描述
     */
    public String getStatusDescription() {
        return success ? "成功" : "失败";
    }

    /**
     * 获取摘要信息
     */
    public String getSummary() {
        return String.format("[%s] %s - %s (%s)", 
                getStatusDescription(),
                toolName,
                result != null ? result.substring(0, Math.min(result.length(), 50)) + "..." : "无结果",
                getFormattedDuration());
    }

    @Override
    public String toString() {
        return String.format("ToolExecutionRecord{toolName='%s', success=%s, duration=%dms, order=%d}", 
                toolName, success, duration, executionOrder);
    }
}

