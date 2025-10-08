package com.zhouruojun.jobsearchagent.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * 教育经历数据结构
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Education {
    
    @JsonProperty("school")
    private String school;
    
    @JsonProperty("degree")
    private String degree;
    
    @JsonProperty("major")
    private String major;
    
    @JsonProperty("range")
    private String range;
    
    @JsonProperty("location")
    private String location;
    
    @JsonProperty("gpa")
    private String gpa;
    
    @JsonProperty("honors")
    private String honors;
    
    @JsonProperty("relevantCourses")
    private String relevantCourses;
}
