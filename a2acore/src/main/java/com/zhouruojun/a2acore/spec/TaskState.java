package com.zhouruojun.a2acore.spec;

import com.fasterxml.jackson.annotation.JsonValue;
import java.io.Serializable;
import lombok.Getter;
import lombok.ToString;

/**
 * <p>
 * 任务状态枚??* 发起任务后，submitted
 * 任务正在执行，working
 * 等待用户输入，input-required
 * 4种完结态：
 *  任务完成：completed
 *  任务取消：canceled
 *  任务失败：failed
 *  未知状态：unknown
 *
 * <a href="https://github.com/google/A2A/blob/main/docs/specification.md#63-taskstate-enum">点击跳转</a>
 * </p>
 *
 */
@ToString
@Getter
public enum TaskState implements Serializable {
    SUBMITTED("submitted"),
    WORKING("working"),
    INPUT_REQUIRED("input-required"),
    ACTION_REQUIRED("action-required"),
    COMPLETED("completed"),
    CANCELED("canceled"),
    FAILED("failed"),
    UNKNOWN("unknown");

    private final String state;

    TaskState(String state) {
        this.state = state;
    }

    @JsonValue
    public String getState() {
        return state;
    }
}


