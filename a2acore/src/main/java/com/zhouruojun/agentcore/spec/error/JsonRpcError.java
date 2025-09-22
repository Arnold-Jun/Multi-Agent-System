package com.zhouruojun.agentcore.spec.error;

import com.zhouruojun.agentcore.spec.Nullable;
import lombok.Data;

/**
 * <p>
 *     <a href="https://github.com/google/A2A/blob/main/docs/specification.md#8-error-handling">点击跳转</a>
 * </p>
 *
 */
@Data
public class JsonRpcError {
    private int code;
    private String message;
    @Nullable
    private Object data;
}
