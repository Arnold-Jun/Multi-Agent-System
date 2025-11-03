package com.zhouruojun.travelingagent.agent.state.subgraph;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 工具执行历史管理类
 * 管理所有工具执行记录，提供查询、统计和格式化功能
 */
@Data
@NoArgsConstructor
@Slf4j
public class ToolExecutionHistory implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 默认最大记录数
     */
    private static final int DEFAULT_MAX_RECORDS = 20;
    
    /**
     * 工具执行记录列表
     */
    private List<ToolExecutionRecord> records = new ArrayList<>();
    
    /**
     * 最大记录数
     */
    private int maxRecords = DEFAULT_MAX_RECORDS;
    
    /**
     * 构造函数
     */
    public ToolExecutionHistory(int maxRecords) {
        this.maxRecords = maxRecords;
        this.records = new ArrayList<>();
    }
    
    /**
     * 添加单个工具执行记录
     */
    public void addRecord(String toolName, String result) {
        addRecord(toolName, result, 0);
    }
    
    /**
     * 添加单个工具执行记录（带耗时）
     */
    public void addRecord(String toolName, String result, long duration) {
        addRecord(toolName, null, result, duration);
    }
    
    /**
     * 添加单个工具执行记录（带参数和耗时）
     */
    public void addRecord(String toolName, String arguments, String result, long duration) {
        int order = records.size() + 1;
        ToolExecutionRecord record = new ToolExecutionRecord(toolName, arguments, result, order);
        record.setDuration(duration);
        
        records.add(record);
        
        // 限制记录数量
        if (records.size() > maxRecords) {
            // 移除最旧的记录
            records = new ArrayList<>(records.subList(records.size() - maxRecords, records.size()));
            // 重新编号
            reorderRecords();
        }
        
        log.debug("添加工具执行记录: {} (总记录数: {})", toolName, records.size());
    }
    
    /**
     * 添加批量工具执行记录
     */
    public void addBatchRecords(List<String> toolNames, List<String> results) {
        if (toolNames.size() != results.size()) {
            throw new IllegalArgumentException("工具名称和结果数量不匹配");
        }
        
        for (int i = 0; i < toolNames.size(); i++) {
            addRecord(toolNames.get(i), results.get(i));
        }
    }
    
    /**
     * 重新编号记录
     */
    private void reorderRecords() {
        for (int i = 0; i < records.size(); i++) {
            records.get(i).setExecutionOrder(i + 1);
        }
    }
    
    /**
     * 获取特定工具的最新执行结果
     */
    public Optional<String> getLatestResult(String toolName) {
        return records.stream()
            .filter(r -> r.getToolName().equals(toolName))
            .reduce((first, second) -> second)
            .map(ToolExecutionRecord::getResult);
    }
    
    /**
     * 获取特定工具的所有执行记录
     */
    public List<ToolExecutionRecord> getRecordsByToolName(String toolName) {
        return records.stream()
            .filter(r -> r.getToolName().equals(toolName))
            .collect(Collectors.toList());
    }
    
    /**
     * 检查是否已执行过某个工具
     */
    public boolean hasExecuted(String toolName) {
        return records.stream()
            .anyMatch(r -> r.getToolName().equals(toolName));
    }
    
    /**
     * 获取最近N条记录
     */
    public List<ToolExecutionRecord> getRecentRecords(int count) {
        int size = records.size();
        if (size <= count) {
            return new ArrayList<>(records);
        }
        return new ArrayList<>(records.subList(size - count, size));
    }
    
    /**
     * 获取成功执行的工具数量
     */
    public long getSuccessCount() {
        return records.stream()
            .filter(ToolExecutionRecord::isSuccess)
            .count();
    }
    
    /**
     * 获取失败执行的工具数量
     */
    public long getFailureCount() {
        return records.stream()
            .filter(r -> !r.isSuccess())
            .count();
    }
    
    /**
     * 获取总执行次数
     */
    public int getTotalCount() {
        return records.size();
    }
    
    /**
     * 清空所有记录
     */
    public void clear() {
        records.clear();
        log.debug("清空工具执行历史");
    }
    
    /**
     * 格式化为LLM可读的字符串（完整版）
     */
    public String toFormattedString() {
        if (records.isEmpty()) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("**工具执行历史记录**（共").append(records.size()).append("次）:\n");
        sb.append("成功: ").append(getSuccessCount()).append("次, ");
        sb.append("失败: ").append(getFailureCount()).append("次\n\n");
        
        for (ToolExecutionRecord record : records) {
            sb.append(record.getFormattedRecord());
            sb.append("\n");
        }
        
        return sb.toString();
    }
    
    /**
     * 格式化为LLM可读的字符串（简化版，只包含最近N次）
     */
    public String toFormattedString(int recentCount) {
        List<ToolExecutionRecord> recentRecords = getRecentRecords(recentCount);
        
        if (recentRecords.isEmpty()) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("**工具执行记录**");

        for (ToolExecutionRecord record : recentRecords) {
            sb.append(record.getFormattedRecord());
            sb.append("\n");
        }
        
        if (records.size() > recentCount) {
            sb.append("\n[还有 ").append(records.size() - recentCount).append(" 条更早的记录]\n");
        }
        
        return sb.toString();
    }
    
    /**
     * 格式化为分组的字符串（按工具名称分组）
     */
    public String toGroupedFormattedString() {
        if (records.isEmpty()) {
            return "";
        }
        
        // 按工具名称分组
        var groupedRecords = records.stream()
            .collect(Collectors.groupingBy(ToolExecutionRecord::getToolName));
        
        StringBuilder sb = new StringBuilder();
        sb.append("**工具执行历史（按工具分组）**:\n\n");
        
        groupedRecords.forEach((toolName, toolRecords) -> {
            sb.append("=== ").append(toolName).append(" ===\n");
            sb.append("执行次数: ").append(toolRecords.size()).append("次\n");
            
            for (ToolExecutionRecord record : toolRecords) {
                sb.append(String.format("  第%d次: %s%s\n",
                    record.getExecutionOrder(),
                    record.isSuccess() ? "✓" : "✗",
                    record.getResult().length() > 50 
                        ? record.getResult().substring(0, 50) + "..." 
                        : record.getResult()));
            }
            sb.append("\n");
        });
        
        return sb.toString();
    }
    
    /**
     * 获取统计信息
     */
    public String getStatistics() {
        if (records.isEmpty()) {
            return "无工具执行记录";
        }
        
        long avgDuration = (long) records.stream()
            .mapToLong(ToolExecutionRecord::getDuration)
            .filter(d -> d > 0)
            .average()
            .orElse(0.0);
        
        return String.format("总执行: %d次, 成功: %d次, 失败: %d次, 平均耗时: %dms",
            getTotalCount(),
            getSuccessCount(),
            getFailureCount(),
            avgDuration);
    }
}


