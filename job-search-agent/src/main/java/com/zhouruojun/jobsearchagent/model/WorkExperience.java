package com.zhouruojun.jobsearchagent.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

/**
 * 工作经历数据结构
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkExperience {
    
    @JsonProperty("company")
    private String company;
    
    @JsonProperty("role")
    private String role;
    
    @JsonProperty("range")
    private String range;
    
    @JsonProperty("location")
    private String location;
    
    @JsonProperty("bullets")
    private List<String> bullets;
    
    @JsonProperty("description")
    private String description;
    
    @JsonProperty("technologies")
    private List<String> technologies;
    
    @JsonProperty("achievements")
    private List<String> achievements;
}
