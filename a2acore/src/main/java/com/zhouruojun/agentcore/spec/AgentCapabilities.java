package com.zhouruojun.agentcore.spec;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * <p>
 * 智能体能力集 * <a href="https://github.com/google/A2A/blob/main/docs/specification/agent-card.md">点击跳转</a>
 * </p>
 *
 */
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Data
public class AgentCapabilities implements Serializable {
    private boolean streaming;
    private boolean pushNotifications;
    private boolean stateTransitionHistory;
}
