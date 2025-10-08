package com.zhouruojun.a2acore.spec;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * <p>
 * �������??* <a href="https://github.com/google/A2A/blob/main/docs/specification/agent-card.md">�����ת</a>
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


