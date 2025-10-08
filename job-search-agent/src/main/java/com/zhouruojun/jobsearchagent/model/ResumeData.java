package com.zhouruojun.jobsearchagent.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

/**
 * 简历数据结构
 * 用于存储解析后的简历信息和模板渲染
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResumeData {
    
    @JsonProperty("name")
    private String name;
    
    @JsonProperty("title")
    private String title;
    
    @JsonProperty("email")
    private String email;
    
    @JsonProperty("phone")
    private String phone;
    
    @JsonProperty("location")
    private String location;
    
    @JsonProperty("summary")
    private String summary;
    
    @JsonProperty("skills")
    private List<String> skills;
    
    @JsonProperty("experiences")
    private List<WorkExperience> experiences;
    
    @JsonProperty("education")
    private List<Education> education;
    
    @JsonProperty("projects")
    private List<Project> projects;
    
    @JsonProperty("certifications")
    private List<Certification> certifications;
    
    @JsonProperty("languages")
    private List<Language> languages;
    
    @JsonProperty("additionalInfo")
    private String additionalInfo;
}
