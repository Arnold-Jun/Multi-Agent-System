package com.zhouruojun.travelingagent.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * 旅游意图表单数据模型
 * 用于收集用户的结构化旅游规划信息
 */
@Data
public class TravelIntentForm {
    
    /**
     * 目的地（城市/地区，可以包含多个城市，如"云南 昆明-大理-丽江"）
     */
    @JsonProperty("destination")
    private String destination;
    
    /**
     * 出发日期（格式：yyyy-MM-dd）
     */
    @JsonProperty("startDate")
    private String startDate;
    
    /**
     * 旅行天数
     */
    @JsonProperty("days")
    private Integer days;
    
    /**
     * 人数（成人+儿童总数）
     */
    @JsonProperty("peopleCount")
    private Integer peopleCount;
    
    /**
     * 预算档位：economy（经济）、standard（适中）、premium（高端）
     */
    @JsonProperty("budgetRange")
    private String budgetRange;
    
    /**
     * 预算金额（可选，如果提供则优先使用金额）
     */
    @JsonProperty("budgetAmount")
    private Double budgetAmount;
    
    /**
     * 偏好列表：history（历史）、food（美食）、outdoor（户外）、shopping（购物）、family（亲子）、relax（休闲）等
     */
    @JsonProperty("preferences")
    private List<String> preferences;
    
    /**
     * 住宿标准：hostel（青旅）、budget（经济）、comfort（舒适）、luxury（高端）
     */
    @JsonProperty("lodgingLevel")
    private String lodgingLevel;
    
    /**
     * 交通偏好：train（高铁）、flight（飞机）、self-drive（自驾）、none（无偏好）
     */
    @JsonProperty("transportPreference")
    private String transportPreference;
    
    /**
     * 备注信息（自由文本，最多500字符）
     */
    @JsonProperty("notes")
    private String notes;
    
    /**
     * 会话ID
     */
    @JsonProperty("sessionId")
    private String sessionId;
}

