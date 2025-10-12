package com.zhouruojun.jobsearchagent.agent.state.subgraph;

import com.zhouruojun.jobsearchagent.agent.state.SubgraphState;
import com.zhouruojun.jobsearchagent.config.CheckpointConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 子图状态管理器
 * 负责子图状态的保存、加载和管理
 */
@Slf4j
@Component
public class SubgraphStateManager {
    
    @Autowired
    private CheckpointConfig checkpointConfig;
    
    /**
     * 内存缓存，用于快速访问
     */
    private final Map<String, SubgraphExecutionRecord> memoryCache = new ConcurrentHashMap<>();
    
    /**
     * 保存子图状态
     */
    public void saveSubgraphState(String sessionId, String agentName, SubgraphState state) {
        if (!checkpointConfig.getSubgraphPersistence().isEnabled()) {
            log.debug("子图状态持久化未启用，跳过保存");
            return;
        }
        
        try {
            String key = buildStateKey(sessionId, agentName);
            
            // 提取持久化状态
            SubgraphExecutionRecord persistentState = extractPersistentState(sessionId, agentName, state);
            
            // 增加执行次数
            persistentState.incrementExecutionCount();
            
            // 保存到内存缓存
            memoryCache.put(key, persistentState);
            
            log.info("保存子图状态: sessionId={}, agentName={}, executionCount={}", 
                    sessionId, agentName, persistentState.getExecutionCount());
            
        } catch (Exception e) {
            log.error("保存子图状态失败: sessionId={}, agentName={}, error={}", 
                    sessionId, agentName, e.getMessage(), e);
        }
    }
    
    /**
     * 加载子图状态
     */
    public Optional<Map<String, Object>> loadSubgraphState(String sessionId, String agentName) {
        if (!checkpointConfig.getSubgraphPersistence().isEnabled()) {
            log.debug("子图状态持久化未启用，返回空状态");
            return Optional.empty();
        }
        
        try {
            String key = buildStateKey(sessionId, agentName);
            
            // 从内存缓存加载
            SubgraphExecutionRecord persistentState = memoryCache.get(key);
            
            if (persistentState == null) {
                log.debug("未找到子图历史状态: sessionId={}, agentName={}", sessionId, agentName);
                return Optional.empty();
            }
            
            // 检查状态是否有效
            int retentionDays = checkpointConfig.getSubgraphPersistence().getRetentionDays();
            if (!persistentState.isValid(retentionDays)) {
                log.warn("子图状态已过期: sessionId={}, agentName={}, lastUpdate={}", 
                        sessionId, agentName, persistentState.getLastUpdateTime());
                memoryCache.remove(key);
                return Optional.empty();
            }
            
            // 恢复状态
            Map<String, Object> restoredState = restoreSubgraphState(persistentState);
            
            log.info("加载子图历史状态: sessionId={}, agentName={}, executionCount={}", 
                    sessionId, agentName, persistentState.getExecutionCount());
            
            return Optional.of(restoredState);
            
        } catch (Exception e) {
            log.error("加载子图状态失败: sessionId={}, agentName={}, error={}", 
                    sessionId, agentName, e.getMessage(), e);
            return Optional.empty();
        }
    }
    
    /**
     * 清除特定会话的子图状态
     */
    public void clearSessionStates(String sessionId) {
        memoryCache.entrySet().removeIf(entry -> entry.getKey().startsWith(sessionId + ":"));
        log.info("清除会话的子图状态: sessionId={}", sessionId);
    }

    
    /**
     * 提取需要持久化的状态
     */
    private SubgraphExecutionRecord extractPersistentState(String sessionId, String agentName, SubgraphState state) {
        SubgraphExecutionRecord.SubgraphExecutionRecordBuilder builder = SubgraphExecutionRecord.builder()
                .sessionId(sessionId)
                .agentName(agentName)
                .lastUpdateTime(LocalDateTime.now());
        
        // 获取现有状态（如果存在）
        String key = buildStateKey(sessionId, agentName);
        SubgraphExecutionRecord existingState = memoryCache.get(key);
        if (existingState != null) {
            builder.createTime(existingState.getCreateTime())
                   .executionCount(existingState.getExecutionCount());
        } else {
            builder.createTime(LocalDateTime.now())
                   .executionCount(0);
        }
        
        // 提取工具执行历史
        state.getToolExecutionHistory().ifPresent(history -> {
            builder.toolExecutionHistory(history);
        });
        
        // 提取当前任务信息
        state.getCurrentTask().ifPresent(task -> {
            builder.currentTask(task);
        });
        
        // 提取子图类型
        state.getSubgraphType().ifPresent(type -> {
            builder.subgraphType(type);
        });
        
        // 提取子图上下文
        state.getSubgraphContext().ifPresent(context -> {
            builder.subgraphContext(context);
        });
        
        // 提取最后执行状态
        String lastStatus = state.next().orElse("unknown");
        builder.lastExecutionStatus(lastStatus);
        
        return builder.build();
    }
    
    /**
     * 恢复子图状态
     */
    private Map<String, Object> restoreSubgraphState(SubgraphExecutionRecord persistentState) {
        Map<String, Object> stateData = new HashMap<>();
        
        // 恢复工具执行历史
        if (persistentState.getToolExecutionHistory() != null) {
            stateData.put("toolExecutionHistory", persistentState.getToolExecutionHistory());
            log.debug("恢复工具执行历史: {} 条记录", 
                    persistentState.getToolExecutionHistory().getTotalCount());
        }
        
        // 恢复任务信息
        if (persistentState.getCurrentTask() != null) {
            stateData.put("currentTask", persistentState.getCurrentTask());
        }
        
        // 恢复子图类型
        if (persistentState.getSubgraphType() != null) {
            stateData.put("subgraphType", persistentState.getSubgraphType());
        }
        
        // 恢复子图上下文（如果启用上下文缓存）
        if (checkpointConfig.getSubgraphPersistence().isEnableContextCaching() 
                && persistentState.getSubgraphContext() != null) {
            stateData.put("subgraphContext", persistentState.getSubgraphContext());
            log.debug("恢复子图上下文缓存");
        }
        
        // 添加元数据
        stateData.put("restoredFromPersistence", true);
        stateData.put("executionCount", persistentState.getExecutionCount());
        stateData.put("lastUpdateTime", persistentState.getLastUpdateTime());
        
        return stateData;
    }
    
    /**
     * 构建状态键
     */
    private String buildStateKey(String sessionId, String agentName) {
        return sessionId + ":" + agentName;
    }
    
    /**
     * 从键中提取智能体名称
     */
    private String extractAgentNameFromKey(String key) {
        String[] parts = key.split(":");
        return parts.length > 1 ? parts[1] : "unknown";
    }
}

