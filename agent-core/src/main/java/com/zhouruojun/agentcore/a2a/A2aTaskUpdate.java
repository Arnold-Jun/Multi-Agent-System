package com.zhouruojun.agentcore.a2a;

import com.zhouruojun.agentcore.spec.UpdateEvent;
import lombok.Data;

/**
 * A2A任务更新事件
 *
 */
@Data
public class A2aTaskUpdate {
    /**
     * 任务ID
     */
    private String taskId;
    
    /**
     * 会话ID
     */
    private String sessionId;
    
    /**
     * 更新类型：status, artifact
     */
    private String updateType;
    
    /**
     * 更新事件
     */
    private UpdateEvent updateEvent;
}