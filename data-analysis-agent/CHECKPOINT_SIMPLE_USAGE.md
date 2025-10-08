# Checkpoint配置使用指南

## 📋 简化后的Checkpoint配置

经过简化，checkpoint配置现在非常简洁，只需要两个配置项：

### 1. 配置文件 (application.yml)

```yaml
data-analysis:
  checkpoint:
    enabled: true                    # 是否启用checkpoint功能
    namespace-prefix: "data-analysis" # Checkpoint命名空间前缀
```

### 2. 配置类 (CheckpointConfig.java)

```java
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
```

## 🚀 使用方式

### 自动配置

系统会自动从`CheckpointConfig`中读取配置，无需手动指定：

```java
// DataAnalysisControllerCore中
return new DataAnalysisGraphBuilder()
        .chatLanguageModel(chatLanguageModel)
        .toolCollection(toolCollection)
        .parallelExecutionConfig(parallelExecutionConfig)
        .checkpointSaver(checkpointSaver)
        .checkpointConfig(checkpointConfig)  // 自动传递配置
        .username(username)
        .requestId(requestId)
        .build();
```

### 子图自动应用配置

所有子图会自动应用checkpoint配置：

```java
// 自动应用配置
boolean checkpointEnabled = checkpointConfig != null ? checkpointConfig.isEnabled() : true;
String namespace = checkpointConfig != null ? 
    checkpointConfig.getSubgraphNamespace(agentName) : agentName;
```

## 🎯 配置效果

### 启用checkpoint (enabled: true)
- 所有子图都会启用checkpoint功能
- 状态会被自动保存和恢复
- 支持任务中断后的恢复

### 禁用checkpoint (enabled: false)
- 所有子图都不会启用checkpoint功能
- 每次执行都是全新状态
- 无法恢复中断的任务

### 命名空间管理
- 子图命名空间：`data-analysis-subgraph-{agentName}`
- 主图命名空间：`data-analysis-main-graph`
- 支持多环境隔离

## 📝 总结

简化后的checkpoint配置：

✅ **极简配置**：只需要2个配置项
✅ **自动应用**：无需手动指定
✅ **统一管理**：所有子图使用相同配置
✅ **灵活控制**：可以全局启用/禁用
✅ **命名空间**：支持多环境隔离

这种设计既保持了功能的完整性，又大大简化了配置的复杂性。
