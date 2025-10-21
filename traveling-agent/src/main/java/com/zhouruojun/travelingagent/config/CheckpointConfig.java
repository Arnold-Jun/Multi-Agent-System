package com.zhouruojun.travelingagent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Checkpoint配置类
 */
@Data
@Component
@ConfigurationProperties(prefix = "traveling.checkpoint")
public class CheckpointConfig {
    
    private boolean enabled = true;
    private String namespacePrefix = "traveling";
    
    private SubgraphPersistence subgraphPersistence = new SubgraphPersistence();
    
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
    
    @Data
    public static class SubgraphPersistence {
        private boolean enabled = true;
        private int maxHistoryRecords = 100;
        private int retentionDays = 30;
        private boolean enableToolLearning = true;
        private boolean enableContextCaching = true;
        private boolean enableCrossSessionSharing = false;
    }
}
