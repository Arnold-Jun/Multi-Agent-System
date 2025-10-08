package com.zhouruojun.a2acore.spec;

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
 * <a href="https://github.com/google/A2A/blob/main/docs/specification.md#67-artifact-object"></a>
 * </p>
 *
 */
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class Artifact implements Serializable {
    @Nullable
    private String name;
    @Nullable
    private String description;
    private List<Part> parts;
    @Nullable
    private Map<String, Object> metadata;
    private int index;
    @Nullable
    private Boolean append;
    @Nullable
    private Boolean lastChunk;

    public Artifact(List<Part> parts, int index, Boolean append) {
        this.parts = parts;
        this.index = index;
        this.append = append;
    }
}


