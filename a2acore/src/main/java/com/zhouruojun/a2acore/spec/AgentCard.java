package com.zhouruojun.a2acore.spec;

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
     * ��������??    */
    private String name;
    /**
     * ��������??    */
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


