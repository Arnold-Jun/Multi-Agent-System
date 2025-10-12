package com.zhouruojun.jobsearchagent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Checkpoint配置类
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "job-search.checkpoint")
public class CheckpointConfig {
    
    /**
     * 是否启用checkpoint功能
     */
    private boolean enabled = true;
    
    /**
     * Checkpoint命名空间前缀
     */
    private String namespacePrefix = "job-search";
    
    /**
     * 子图持久化配置
     */
    private SubgraphPersistenceConfig subgraphPersistence = new SubgraphPersistenceConfig();
    
    /**
     * 获取子图checkpoint命名空间
     */
    public String getSubgraphNamespace(String agentName) {
        return namespacePrefix + "-subgraph-" + agentName;
    }
    
    /**
     * 获取主图checkpoint命名空间
     */
    public String getMainGraphNamespace() {
        return namespacePrefix + "-main-graph";
    }
    
    /**
     * 子图持久化配置类
     */
    @Data
    public static class SubgraphPersistenceConfig {
        /**
         * 是否启用子图状态持久化
         */
        private boolean enabled = true;
        
        /**
         * 最大历史记录数
         */
        private int maxHistoryRecords = 100;
        
        /**
         * 状态保留天数
         */
        private int retentionDays = 30;
        
        /**
         * 是否启用工具学习
         */
        private boolean enableToolLearning = true;
        
        /**
         * 是否启用上下文缓存
         */
        private boolean enableContextCaching = true;
        
        /**
         * 是否启用跨会话共享
         */
        private boolean enableCrossSessionSharing = false;
    }
}

