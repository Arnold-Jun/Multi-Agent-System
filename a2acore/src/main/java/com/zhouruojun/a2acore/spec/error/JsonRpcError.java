package com.zhouruojun.a2acore.spec.error;

import com.zhouruojun.a2acore.spec.Nullable;
import lombok.Data;

/**
 * <p>
 *     <a href="https://github.com/google/A2A/blob/main/docs/specification.md#8-error-handling">µã»÷Ìø×ª</a>
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


