package com.zhouruojun.jobsearchagent.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.Map;

/**
 * 简历模板数据结构
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResumeTemplate {
    
    @JsonProperty("templateId")
    private String templateId;
    
    @JsonProperty("templateName")
    private String templateName;
    
    @JsonProperty("category")
    private String category;
    
    @JsonProperty("description")
    private String description;
    
    @JsonProperty("templatePath")
    private String templatePath;
    
    @JsonProperty("previewImage")
    private String previewImage;
    
    @JsonProperty("templateVariables")
    private Map<String, Object> templateVariables;
    
    @JsonProperty("isDefault")
    private boolean isDefault;
    
    @JsonProperty("version")
    private String version;
}
