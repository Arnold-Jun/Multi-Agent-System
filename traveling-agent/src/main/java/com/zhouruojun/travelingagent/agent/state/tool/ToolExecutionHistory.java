package com.zhouruojun.travelingagent.agent.state.tool;

import lombok.Data;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 工具执行历史
 * 记录子图中工具的执行历史
 */
@Data
public class ToolExecutionHistory {

    private List<ToolExecutionRecord> records = new ArrayList<>();
    private int maxRecords = 100; // 最大记录数

    /**
     * 添加执行记录
     */
    public void addRecord(ToolExecutionRecord record) {
        records.add(record);
        
        // 限制记录数量
        if (records.size() > maxRecords) {
            records.remove(0);
        }
    }

    /**
     * 添加执行记录（便捷方法）
     */
    public void addRecord(String toolName, String result, boolean success) {
        ToolExecutionRecord record = new ToolExecutionRecord();
        record.setToolName(toolName);
        record.setResult(result);
        record.setSuccess(success);
        record.setExecutionOrder(records.size() + 1);
        record.setTimestamp(System.currentTimeMillis());
        addRecord(record);
    }

    /**
     * 获取所有记录
     */
    public List<ToolExecutionRecord> getAllRecords() {
        return new ArrayList<>(records);
    }

    /**
     * 获取指定工具的记录
     */
    public List<ToolExecutionRecord> getRecordsForTool(String toolName) {
        return records.stream()
                .filter(record -> toolName.equals(record.getToolName()))
                .collect(Collectors.toList());
    }

    /**
     * 获取最新的记录
     */
    public Optional<ToolExecutionRecord> getLatestRecord() {
        if (records.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(records.get(records.size() - 1));
    }

    /**
     * 获取指定工具的最近记录
     */
    public Optional<ToolExecutionRecord> getLatestRecordForTool(String toolName) {
        return records.stream()
                .filter(record -> toolName.equals(record.getToolName()))
                .max(Comparator.comparing(ToolExecutionRecord::getExecutionOrder));
    }

    /**
     * 检查工具是否已执行
     */
    public boolean hasToolBeenExecuted(String toolName) {
        return records.stream()
                .anyMatch(record -> toolName.equals(record.getToolName()));
    }

    /**
     * 获取执行统计
     */
    public Map<String, Long> getExecutionStatistics() {
        return records.stream()
                .collect(Collectors.groupingBy(ToolExecutionRecord::getToolName, Collectors.counting()));
    }

    /**
     * 获取成功执行统计
     */
    public Map<String, Long> getSuccessStatistics() {
        return records.stream()
                .filter(ToolExecutionRecord::isSuccess)
                .collect(Collectors.groupingBy(ToolExecutionRecord::getToolName, Collectors.counting()));
    }

    /**
     * 获取失败执行统计
     */
    public Map<String, Long> getFailureStatistics() {
        return records.stream()
                .filter(record -> !record.isSuccess())
                .collect(Collectors.groupingBy(ToolExecutionRecord::getToolName, Collectors.counting()));
    }

    /**
     * 获取执行历史摘要
     */
    public String getExecutionSummary() {
        if (records.isEmpty()) {
            return "暂无工具执行历史";
        }

        StringBuilder summary = new StringBuilder();
        summary.append(String.format("工具执行历史摘要（共%d条记录）：\n", records.size()));

        Map<String, Long> stats = getExecutionStatistics();
        for (Map.Entry<String, Long> entry : stats.entrySet()) {
            String toolName = entry.getKey();
            long totalCount = entry.getValue();
            long successCount = getSuccessStatistics().getOrDefault(toolName, 0L);
            long failureCount = getFailureStatistics().getOrDefault(toolName, 0L);

            summary.append(String.format("- %s: 总计%d次，成功%d次，失败%d次\n",
                    toolName, totalCount, successCount, failureCount));
        }

        return summary.toString();
    }

    /**
     * 获取最近执行的工具列表
     */
    public List<String> getRecentlyExecutedTools(int count) {
        return records.stream()
                .sorted(Comparator.comparing(ToolExecutionRecord::getExecutionOrder).reversed())
                .limit(count)
                .map(ToolExecutionRecord::getToolName)
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * 清空历史记录
     */
    public void clear() {
        records.clear();
    }

    /**
     * 检查是否为空
     */
    public boolean isEmpty() {
        return records.isEmpty();
    }

    /**
     * 获取记录数量
     */
    public int size() {
        return records.size();
    }

    /**
     * 获取格式化的历史记录（用于LLM）
     */
    public String getFormattedHistory() {
        if (records.isEmpty()) {
            return "暂无工具执行历史";
        }

        StringBuilder formatted = new StringBuilder();
        formatted.append("工具执行历史：\n");

        for (ToolExecutionRecord record : records) {
            formatted.append(String.format("- [%s] %s: %s\n",
                    record.isSuccess() ? "成功" : "失败",
                    record.getToolName(),
                    record.getResult()));
        }

        return formatted.toString();
    }

    @Override
    public String toString() {
        return String.format("ToolExecutionHistory{records=%d, maxRecords=%d}", 
                records.size(), maxRecords);
    }
}

