package com.zhouruojun.a2acore.spec;

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
 * <a href="https://github.com/google/A2A/blob/main/docs/specification.md#65-part-union-type"></a>
 * </p>
 */
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        // ���ݴ����ͷ�����??
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


