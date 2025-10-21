package com.zhouruojun.travelingagent.agent.state.main;

/**
 * 任务状态枚举
 */
public enum TaskStatus {
    
    PENDING("pending", "待执行"),
    IN_PROGRESS("in_progress", "执行中"),
    COMPLETED("completed", "已完成"),
    FAILED("failed", "失败");
    
    private final String code;
    private final String description;
    
    TaskStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }
    
    public String getCode() {
        return code;
    }
    
    public String getDescription() {
        return description;
    }
    
    public static TaskStatus fromCode(String code) {
        for (TaskStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown task status: " + code);
    }
}


