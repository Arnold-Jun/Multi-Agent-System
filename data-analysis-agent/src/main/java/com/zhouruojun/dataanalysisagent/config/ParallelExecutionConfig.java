package com.zhouruojun.dataanalysisagent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 并行执行配置类
 * 用于配置工具并行执行的参数
 */
@Data
@Component
@ConfigurationProperties(prefix = "data-analysis.parallel-execution")
public class ParallelExecutionConfig {
    
    /**
     * 是否启用并行执行
     */
    private boolean enabled = true;
    
    /**
     * 并行执行超时时间（秒）
     */
    private int timeoutSeconds = 30;
    
    /**
     * 线程池核心线程数
     */
    private int corePoolSize = 4;
    
    /**
     * 线程池最大线程数
     */
    private int maxPoolSize = 8;
    
    /**
     * 线程空闲时间（秒）
     */
    private int keepAliveSeconds = 60;
    
    /**
     * 队列容量
     */
    private int queueCapacity = 100;
    
    /**
     * 是否启用并行执行日志
     */
    private boolean enableLogging = true;
    
    /**
     * 并行执行的最小工具数量阈值
     * 当工具数量大于等于此值时，才使用并行执行
     */
    private int minToolsForParallel = 2;
    
    /**
     * 检查配置是否有效
     */
    public boolean isValid() {
        return timeoutSeconds > 0 
               && corePoolSize > 0 
               && maxPoolSize >= corePoolSize
               && keepAliveSeconds > 0
               && queueCapacity > 0
               && minToolsForParallel > 0;
    }
}
