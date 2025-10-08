package com.zhouruojun.jobsearchagent.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * 语言能力数据结构
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Language {
    
    @JsonProperty("language")
    private String language;
    
    @JsonProperty("proficiency")
    private String proficiency;
    
    @JsonProperty("certification")
    private String certification;
    
    @JsonProperty("score")
    private String score;
}
