package com.zhouruojun.agentcore.spec;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * <p>
 * 任务状态变更事件，用于tasks/sendSubscribe场景
 * </p>
 *
 */
@ToString
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Data
public class TaskStatusUpdateEvent extends UpdateEvent {
    private TaskStatus status;
    @JsonProperty("final")
    private boolean finalFlag;


    public TaskStatusUpdateEvent(String id, TaskStatus taskStatus, boolean finalFlag) {
        super.setId(id);
        this.status = taskStatus;
        this.finalFlag = finalFlag;
    }
}

