package com.zhouruojun.agentcore.spec;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * <p>
 * 更新事件基类
 * </p>
 */
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "_type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = TaskStatusUpdateEvent.class, name = "status"),
        @JsonSubTypes.Type(value = TaskArtifactUpdateEvent.class, name = "artifact")
})
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Data
public class UpdateEvent {
    protected String id;
    protected Map<String, Object> metadata;
}
