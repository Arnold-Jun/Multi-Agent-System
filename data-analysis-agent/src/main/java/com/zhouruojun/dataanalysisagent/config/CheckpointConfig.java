package com.zhouruojun.dataanalysisagent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Checkpoint配置类
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "data-analysis.checkpoint")
public class CheckpointConfig {
    
    /**
     * 是否启用checkpoint功能
     */
    private boolean enabled = true;
    
    /**
     * Checkpoint命名空间前缀
     */
    private String namespacePrefix = "data-analysis";
    
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
}
