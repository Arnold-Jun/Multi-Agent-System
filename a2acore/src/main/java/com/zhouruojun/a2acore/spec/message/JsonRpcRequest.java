package com.zhouruojun.a2acore.spec.message;

import com.zhouruojun.a2acore.spec.Nullable;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * <p>
 *     <a href="https://github.com/google/A2A/blob/main/docs/specification.md#7-protocol-rpc-methods">µã»÷Ìø×ª</a>
 * </p>
 *
 */
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@Data
public class JsonRpcRequest<T> extends JsonRpcMessage {
    protected String method;
    @Nullable
    protected T params;
}


