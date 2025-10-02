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
 * ��Ϣ * <a href="https://github.com/google/A2A/blob/main/docs/specification.md#64-message-object">�����ת</a>
 * </p>
 *
 */
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class Message implements Serializable {
    private Role role;
    private List<Part> parts;
    @Nullable
    private Map<String, Object> metadata;

}


