package com.zhouruojun.agentcore.spec;

import java.io.Serializable;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * <p>
 * 文本数据
 * <a href="https://github.com/google/A2A/blob/main/docs/specification.md#651-textpart-object">点击跳转</a>
 * </p>
 *
 */
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@Data
public class TextPart extends Part implements Serializable {
    private String text;

    public TextPart() {
        this.type = "text";
    }

    public TextPart(String text) {
        this.type = "text";
        this.text = text;
    }

    public TextPart(String type, Map<String, Object> metadata, String text) {
        super(type, metadata);
        this.text = text;
    }
}
