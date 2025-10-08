package com.zhouruojun.a2acore.spec;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * <p>
 * <a href="https://github.com/google/A2A/blob/main/docs/specification.md#723-taskartifactupdateevent-object"></a>
 * </p>
 *
 */
@ToString(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Data
public class TaskArtifactUpdateEvent extends UpdateEvent {
    private Artifact artifact;

    public TaskArtifactUpdateEvent(String id, Artifact artifact) {
        super.setId(id);
        this.artifact = artifact;
    }
}


