package com.zhouruojun.jobsearchagent.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

/**
 * 项目经验数据结构
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Project {
    
    @JsonProperty("name")
    private String name;
    
    @JsonProperty("description")
    private String description;
    
    @JsonProperty("role")
    private String role;
    
    @JsonProperty("range")
    private String range;
    
    @JsonProperty("technologies")
    private List<String> technologies;
    
    @JsonProperty("achievements")
    private List<String> achievements;
    
    @JsonProperty("url")
    private String url;
    
    @JsonProperty("github")
    private String github;
}
