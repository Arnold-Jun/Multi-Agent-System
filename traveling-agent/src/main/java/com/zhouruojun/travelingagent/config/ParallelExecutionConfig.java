package com.zhouruojun.travelingagent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 并行执行配置类
 */
@Data
@Component
@ConfigurationProperties(prefix = "traveling.parallel-execution")
public class ParallelExecutionConfig {
    
    private boolean enabled = true;
    private int timeoutSeconds = 120;
    private int corePoolSize = 4;
    private int maxPoolSize = 8;
    private int keepAliveSeconds = 60;
    private int queueCapacity = 100;
    private boolean enableLogging = true;
    private int minToolsForParallel = 2;
    
    /**
     * 检查是否应该使用并行执行
     */
    public boolean shouldUseParallelExecution(int toolCount) {
        return enabled && toolCount >= minToolsForParallel;
    }
}

