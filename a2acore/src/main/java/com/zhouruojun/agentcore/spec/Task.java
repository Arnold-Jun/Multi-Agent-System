package com.zhouruojun.agentcore.spec;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * <p>
 * 任务实体
 * <a href="https://github.com/google/A2A/blob/main/docs/specification.md#61-task-object">点击跳转</a>
 * </p>
 *
 */
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class Task implements Serializable {
    private String id;
    @Nullable
    private String sessionId;
    private TaskStatus status;
    @Nullable
    private List<Artifact> artifacts;
    @Nullable
    private List<Message> history;
    @Nullable
    private Map<String, Object> metadata;

}
