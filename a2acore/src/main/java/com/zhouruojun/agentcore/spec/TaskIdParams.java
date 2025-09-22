package com.zhouruojun.agentcore.spec;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * <p>
 * 任务Id与元数据，用于tasks/cancel场景
 * <a href="https://github.com/google/A2A/blob/main/docs/specification.md#74-taskscancel">点击跳转</a>
 * </p>
 *
 */
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Data
public class TaskIdParams {
    private String id;
    private Map<String, Object> metadata;
}
