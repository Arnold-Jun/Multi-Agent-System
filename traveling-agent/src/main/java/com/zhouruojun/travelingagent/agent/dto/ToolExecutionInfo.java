package com.zhouruojun.travelingagent.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 工具执行信息DTO
 * 用于前端可视化工具调用过程
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolExecutionInfo implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 批次ID（用于唯一标识一次工具执行，前端根据此ID更新对应消息）
     */
    private String batchId;
    
    /**
     * 节点名称（通常是"executeTools"）
     */
    private String nodeName;
    
    /**
     * 工具执行记录列表
     */
    private List<ToolExecutionDetail> toolExecutions;
    
    /**
     * 执行模式（parallel/sequential）
     */
    private String executionMode;
    
    /**
     * 总工具数量
     */
    private int totalTools;
    
    /**
     * 成功执行数量
     */
    private int successCount;
    
    /**
     * 失败执行数量
     */
    private int failureCount;
    
    /**
     * 执行时间戳
     */
    private long timestamp;
    
    /**
     * 单个工具执行详情
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolExecutionDetail implements Serializable {
        
        private static final long serialVersionUID = 1L;
        
        /**
         * 工具名称
         */
        private String toolName;
        
        /**
         * 工具参数（JSON字符串）
         */
        private String arguments;
        
        /**
         * 执行结果（可能被截断以节省传输）
         */
        private String result;
        
        /**
         * 执行是否成功
         */
        private boolean success;
        
        /**
         * 执行耗时（毫秒）
         */
        private long duration;
        
        /**
         * 执行顺序（从1开始）
         */
        private int executionOrder;
        
        /**
         * 是否正在执行（用于实时更新）
         */
        @Builder.Default
        private boolean executing = false;
        
        /**
         * 错误信息（如果执行失败）
         */
        private String errorMessage;
    }
}

