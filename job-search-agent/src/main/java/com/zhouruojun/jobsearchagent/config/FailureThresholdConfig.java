package com.zhouruojun.jobsearchagent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 失败阈值配置类
 * 用于配置任务失败的最大重试次数
 */
@Data
@Component
@ConfigurationProperties(prefix = "job-search.failure-threshold")
public class FailureThresholdConfig {
    
    /**
     * 默认最大失败次数
     */
    private int defaultMaxFailures = 3;
    
    /**
     * 检查配置是否有效
     */
    public boolean isValid() {
        return defaultMaxFailures > 0;
    }
    
    /**
     * 获取最大失败次数
     */
    public int getMaxFailures() {
        return defaultMaxFailures;
    }
}

