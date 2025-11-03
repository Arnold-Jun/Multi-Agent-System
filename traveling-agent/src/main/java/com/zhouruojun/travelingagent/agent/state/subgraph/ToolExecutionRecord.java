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
     * 工具调用参数（JSON字符串格式）
     */
    private String arguments;
    
    /**
     * 简化构造函数
     */
    public ToolExecutionRecord(String toolName, String result, int executionOrder) {
        this(toolName, null, result, executionOrder);
    }
    
    /**
     * 带参数的构造函数
     */
    public ToolExecutionRecord(String toolName, String arguments, String result, int executionOrder) {
        this.toolName = toolName;
        this.arguments = arguments;
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
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%d. 工具: %s%s\n", 
                executionOrder,
                toolName,
                success ? "" : " [失败]"));
        
        // 如果有参数信息，显示参数
        if (arguments != null && !arguments.trim().isEmpty()) {
            sb.append("   参数: ").append(formatArguments(arguments)).append("\n");
        }
        
        sb.append("   结果: ").append(result).append("\n");
        
        return sb.toString();
    }
    
    /**
     * 格式化参数字符串
     * 如果是JSON字符串，尝试美化输出；否则直接返回
     * 注意：调用方已确保args不为null且非空
     */
    private String formatArguments(String args) {
        String trimmed = args.trim();
        
        // 如果参数过长，统一截断
        if (trimmed.length() > 200) {
            return trimmed.substring(0, 200) + "...（已截断）";
        }
        
        return trimmed;
    }
}


