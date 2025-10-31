package com.zhouruojun.travelingagent.agent.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 处理后的旅游意图结果
 * 包含标准化和验证后的信息，用于传递给Planner
 */
@Data
public class TravelIntentResult implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 目的地（标准化后的格式）
     */
    private String destination;
    
    /**
     * 出发日期
     */
    private LocalDate startDate;
    
    /**
     * 结束日期（根据startDate和days计算）
     */
    private LocalDate endDate;
    
    /**
     * 旅行天数
     */
    private Integer days;
    
    /**
     * 人数
     */
    private Integer peopleCount;
    
    /**
     * 预算档位
     */
    private String budgetRange;
    
    /**
     * 预算金额（人民币，元）
     */
    private Double budgetAmount;
    
    /**
     * 预算金额范围（根据档位或金额计算）
     */
    private String budgetRangeText;
    
    /**
     * 偏好列表
     */
    private List<String> preferences;
    
    /**
     * 住宿标准
     */
    private String lodgingLevel;
    
    /**
     * 交通偏好
     */
    private String transportPreference;
    
    /**
     * 备注信息
     */
    private String notes;
    
    /**
     * 是否信息完整（用于判断是否可以进入planner）
     */
    private boolean complete;
    
    /**
     * 缺失的必填字段列表
     */
    private List<String> missingFields = new ArrayList<>();
    
    /**
     * 从TravelIntentForm创建TravelIntentResult
     */
    public static TravelIntentResult fromForm(TravelIntentForm form) {
        TravelIntentResult result = new TravelIntentResult();
        
        // 处理目的地
        result.destination = form.getDestination() != null ? form.getDestination().trim() : null;
        
        // 处理日期
        if (form.getStartDate() != null && !form.getStartDate().trim().isEmpty()) {
            try {
                result.startDate = LocalDate.parse(form.getStartDate().trim(), DateTimeFormatter.ISO_DATE);
            } catch (Exception e) {
                // 解析失败，保持为null
                result.startDate = null;
            }
        }
        
        result.days = form.getDays();
        result.peopleCount = form.getPeopleCount();
        result.budgetRange = form.getBudgetRange();
        result.budgetAmount = form.getBudgetAmount();
        
        // 处理偏好
        result.preferences = form.getPreferences() != null ? new ArrayList<>(form.getPreferences()) : new ArrayList<>();
        
        result.lodgingLevel = form.getLodgingLevel();
        result.transportPreference = form.getTransportPreference();
        result.notes = form.getNotes() != null ? form.getNotes().trim() : null;
        
        // 计算结束日期
        if (result.startDate != null && result.days != null && result.days > 0) {
            result.endDate = result.startDate.plusDays(result.days - 1);
        }
        
        // 计算预算文本
        result.budgetRangeText = calculateBudgetRangeText(result.budgetRange, result.budgetAmount);
        
        // 验证完整性
        result.validateCompleteness();
        
        return result;
    }
    
    /**
     * 验证信息完整性
     */
    private void validateCompleteness() {
        missingFields = new ArrayList<>();
        
        if (destination == null || destination.isEmpty()) {
            missingFields.add("destination");
        }
        
        // 至少需要startDate或days之一
        if (startDate == null && (days == null || days <= 0)) {
            missingFields.add("startDate或days");
        }
        
        if (peopleCount == null || peopleCount <= 0) {
            missingFields.add("peopleCount");
        }
        
        // 预算信息至少需要一项
        if ((budgetRange == null || budgetRange.isEmpty()) && budgetAmount == null) {
            missingFields.add("budgetRange或budgetAmount");
        }
        
        complete = missingFields.isEmpty();
    }
    
    /**
     * 计算预算文本描述
     */
    private static String calculateBudgetRangeText(String budgetRange, Double budgetAmount) {
        if (budgetAmount != null && budgetAmount > 0) {
            return String.format("预算约%.0f元", budgetAmount);
        }
        
        if (budgetRange != null) {
            switch (budgetRange.toLowerCase()) {
                case "economy":
                    return "经济型预算（人均1000-2000元）";
                case "standard":
                    return "适中型预算（人均2000-5000元）";
                case "premium":
                    return "高端型预算（人均5000元以上）";
                default:
                    return "预算：" + budgetRange;
            }
        }
        
        return "未指定预算";
    }
    
    /**
     * 生成用于Planner的任务描述文本
     */
    public String toPlannerTaskDescription() {
        StringBuilder sb = new StringBuilder();
        
        sb.append("用户旅游规划需求：\n");
        
        if (destination != null) {
            sb.append("- 目的地：").append(destination).append("\n");
        }
        
        if (startDate != null) {
            sb.append("- 出发日期：").append(startDate.format(DateTimeFormatter.ofPattern("yyyy年MM月dd日"))).append("\n");
        }
        
        if (days != null && days > 0) {
            sb.append("- 旅行天数：").append(days).append("天\n");
        }
        
        if (endDate != null) {
            sb.append("- 结束日期：").append(endDate.format(DateTimeFormatter.ofPattern("yyyy年MM月dd日"))).append("\n");
        }
        
        if (peopleCount != null && peopleCount > 0) {
            sb.append("- 人数：").append(peopleCount).append("人\n");
        }
        
        if (budgetRangeText != null) {
            sb.append("- ").append(budgetRangeText).append("\n");
        }
        
        if (preferences != null && !preferences.isEmpty()) {
            sb.append("- 偏好：").append(String.join("、", preferences)).append("\n");
        }
        
        if (lodgingLevel != null && !lodgingLevel.isEmpty()) {
            String lodgingText = switch (lodgingLevel.toLowerCase()) {
                case "hostel" -> "青旅";
                case "budget" -> "经济型";
                case "comfort" -> "舒适型";
                case "luxury" -> "高端型";
                default -> lodgingLevel;
            };
            sb.append("- 住宿标准：").append(lodgingText).append("\n");
        }
        
        if (transportPreference != null && !transportPreference.isEmpty() && !"none".equalsIgnoreCase(transportPreference)) {
            String transportText = switch (transportPreference.toLowerCase()) {
                case "train" -> "高铁";
                case "flight" -> "飞机";
                case "self-drive" -> "自驾";
                default -> transportPreference;
            };
            sb.append("- 交通偏好：").append(transportText).append("\n");
        }
        
        if (notes != null && !notes.trim().isEmpty()) {
            sb.append("- 备注：").append(notes).append("\n");
        }
        
        return sb.toString();
    }
}

