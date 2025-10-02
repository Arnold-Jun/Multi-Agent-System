package com.zhouruojun.a2acore.spec;

import java.io.Serializable;
import lombok.Data;
import lombok.ToString;

/**
 * <p>
 * 文件数据的内 * <a href="https://github.com/google/A2A/blob/main/docs/specification.md#66-filecontent-object">点击跳转</a>
 * </p>
 *
 */
@ToString
@Data
public class FileContent implements Serializable {
    @Nullable
    private String name;
    @Nullable
    private String mimeType;
    @Nullable
    private String bytes;
    @Nullable
    private String uri;

    //    @model_validator(mode="after")
    public void checkContent() {
        if (this.bytes != null && uri != null) {
            throw new ValueError("Either 'bytes' or 'uri' must be present in the file data");
        }
        if (this.bytes == null && uri == null) {
            throw new ValueError("Only one of 'bytes' or 'uri' can be present in the file data");
        }
    }

}


