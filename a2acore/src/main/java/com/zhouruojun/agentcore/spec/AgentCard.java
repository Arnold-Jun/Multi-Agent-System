package com.zhouruojun.agentcore.spec;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;


@ToString
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AgentCard {
    /**
     * 智能体名称     */
    private String name;
    /**
     * 智能体描述     */
    @Nullable
    private String description;
    private String url;
    private AgentProvider provider;
    private String version;
    @Nullable
    private String documentationUrl;
    private AgentCapabilities capabilities;
    @Nullable
    private AgentAuthentication authentication;
    private List<String> defaultInputModes;
    private List<String> defaultOutputModes;
    private List<AgentSkill> skills;



}
