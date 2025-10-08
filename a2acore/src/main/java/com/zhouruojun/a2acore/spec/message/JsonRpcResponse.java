package com.zhouruojun.a2acore.spec.message;

import com.zhouruojun.a2acore.spec.Nullable;
import com.zhouruojun.a2acore.spec.error.JsonRpcError;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * <p>
 *     <a href="https://github.com/google/A2A/blob/main/docs/specification.md#7-protocol-rpc-methods"></a>
 * </p>
 *
 */
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@Data
public class JsonRpcResponse<T> extends JsonRpcMessage {
    @Nullable
    protected T result;
    @Nullable
    protected JsonRpcError error;

    public JsonRpcResponse() {
    }

    public JsonRpcResponse(String id, JsonRpcError error) {
        this.setId(id);
        this.error = error;
    }
}


