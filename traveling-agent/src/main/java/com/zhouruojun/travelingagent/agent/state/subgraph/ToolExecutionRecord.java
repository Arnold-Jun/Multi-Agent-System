package com.zhouruojun.travelingagent.agent.state.subgraph;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 工具执行记录
 * 记录单个工具的执行信息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ToolExecutionRecord implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 工具名称
     */
    private String toolName;
    
    /**
     * 执行结果
     */
    private String result;
    
    /**
     * 执行顺序（从1开始）
     */
    private int executionOrder;
    
    /**
     * 执行时间戳
     */
    private long timestamp;
    
    /**
     * 是否执行成功
     */
    private boolean success;
    
    /**
     * 执行耗时（毫秒）
     */
    private long duration;
    
    /**
     * 简化构造函数
     */
    public ToolExecutionRecord(String toolName, String result, int executionOrder) {
        this.toolName = toolName;
        this.result = result;
        this.executionOrder = executionOrder;
        this.timestamp = System.currentTimeMillis();
        this.success = !result.startsWith("Error");
        this.duration = 0;
    }
    
    /**
     * 获取格式化的执行记录
     */
    public String getFormattedRecord() {
        return String.format("%d. 工具: %s%s\n   结果: %s\n",
                executionOrder,
                toolName,
                success ? "" : " [失败]",
                result);
    }
}


