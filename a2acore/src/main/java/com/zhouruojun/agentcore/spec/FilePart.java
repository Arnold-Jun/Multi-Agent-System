package com.zhouruojun.agentcore.spec;

import java.io.Serializable;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * <p>
 * 文件数据
 * <a href="https://github.com/google/A2A/blob/main/docs/specification.md#652-filepart-object">点击跳转</a>
 * </p>
 *
 */
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@Data
public class FilePart extends Part implements Serializable {
    private FileContent file;

    public FilePart() {
        super.setType("file");
    }

    public FilePart(String type, Map<String, Object> metadata, FileContent file) {
        super(type, metadata);
        this.file = file;
    }
}
