package com.zhouruojun.a2acore.spec;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * <p>
 * <a href="https://github.com/google/A2A/blob/main/docs/specification/agent-card.md"></a>
 * </p>
 *
 */
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Data
public class AgentProvider implements Serializable {
    private String organization;
    @Nullable
    private String url;
}


