package com.zhouruojun.travelingagent.agent.state.subgraph;

import com.zhouruojun.travelingagent.agent.state.todo.TodoTask;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 持久化的子图状态
 * 用于保存和恢复子图的关键状态信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubgraphExecutionRecord implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 会话ID
     */
    private String sessionId;
    
    /**
     * 智能体名称
     */
    private String agentName;
    
    /**
     * 子图类型
     */
    private String subgraphType;
    
    /**
     * 工具执行历史
     */
    private ToolExecutionHistory toolExecutionHistory;
    
    /**
     * 当前任务信息
     */
    private TodoTask currentTask;
    
    /**
     * 子图上下文
     */
    private String subgraphContext;
    
    /**
     * 最后更新时间
     */
    private LocalDateTime lastUpdateTime;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
    
    /**
     * 执行次数统计
     */
    private int executionCount;
    
    /**
     * 最后执行状态
     */
    private String lastExecutionStatus;
    
    /**
     * 检查状态是否有效（未过期）
     */
    public boolean isValid(int retentionDays) {
        if (lastUpdateTime == null) {
            return false;
        }
        
        LocalDateTime expiryTime = lastUpdateTime.plusDays(retentionDays);
        return LocalDateTime.now().isBefore(expiryTime);
    }
    
    /**
     * 增加执行次数
     */
    public void incrementExecutionCount() {
        this.executionCount++;
        this.lastUpdateTime = LocalDateTime.now();
    }
}