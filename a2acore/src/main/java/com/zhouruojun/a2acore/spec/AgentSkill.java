package com.zhouruojun.a2acore.spec;

import java.io.Serializable;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * <p>
 * ? * <a href="https://github.com/google/A2A/blob/main/docs/specification/agent-card.md"></a>
 * </p>
 *
 */
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class AgentSkill implements Serializable {
    private String id;
    private String name;
    @Nullable
    private String description;
    private List<String> tags;
    private List<String> examples;
    private List<String> inputModes;
    private List<String> outputModes;
}


