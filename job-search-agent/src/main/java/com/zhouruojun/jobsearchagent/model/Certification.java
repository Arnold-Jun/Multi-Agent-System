package com.zhouruojun.jobsearchagent.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * 认证证书数据结构
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Certification {
    
    @JsonProperty("name")
    private String name;
    
    @JsonProperty("issuer")
    private String issuer;
    
    @JsonProperty("date")
    private String date;
    
    @JsonProperty("expiryDate")
    private String expiryDate;
    
    @JsonProperty("credentialId")
    private String credentialId;
    
    @JsonProperty("url")
    private String url;
}
