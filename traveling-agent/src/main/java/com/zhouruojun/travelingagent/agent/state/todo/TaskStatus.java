package com.zhouruojun.travelingagent.agent.state.todo;

/**
 * 任务状态枚举
 */
public enum TaskStatus {
    PENDING("待处理"),
    IN_PROGRESS("进行中"),
    COMPLETED("已完成"),
    FAILED("失败");

    private final String displayName;

    TaskStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}

