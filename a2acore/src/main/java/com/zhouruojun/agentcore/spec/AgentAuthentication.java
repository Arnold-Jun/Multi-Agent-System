package com.zhouruojun.agentcore.spec;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * <p>
 * 智能体鉴权 * <a href="https://github.com/google/A2A/blob/main/docs/specification/agent-card.md">点击跳转</a>
 * </p>
 *
 */
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Data
public class AgentAuthentication {
    private List<String> schemes;
    @Nullable
    private String credentials;
}
