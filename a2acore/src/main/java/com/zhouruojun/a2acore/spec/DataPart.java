package com.zhouruojun.a2acore.spec;

import java.io.Serializable;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * <p>
 * <a href="https://github.com/google/A2A/blob/main/docs/specification.md#653-datapart-object">/a>
 * </p>
 *
 */
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@Data
public class DataPart extends Part implements Serializable {
    private Map<String, Object> data;

    public DataPart() {
        super.setType("data");
    }

    public DataPart(String type, Map<String, Object> metadata, Map<String, Object> data) {
        super(type, metadata);
        this.data = data;
    }
}


