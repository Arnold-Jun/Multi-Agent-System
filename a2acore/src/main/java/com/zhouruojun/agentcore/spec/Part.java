package com.zhouruojun.agentcore.spec;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.io.Serializable;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * <p>
 * 数据基类
 * <a href="https://github.com/google/A2A/blob/main/docs/specification.md#65-part-union-type">点击跳转</a>
 * </p>
 */
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        // 根据此类型反序列化
        property = "_type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = TextPart.class, name = "text"),
        @JsonSubTypes.Type(value = FilePart.class, name = "file"),
        @JsonSubTypes.Type(value = DataPart.class, name = "data")
})
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Data
public class Part implements Serializable {
    protected String type;
    @Nullable
    protected Map<String, Object> metadata;
}
