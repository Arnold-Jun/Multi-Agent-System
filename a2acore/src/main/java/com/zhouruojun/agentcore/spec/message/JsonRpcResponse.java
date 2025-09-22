package com.zhouruojun.agentcore.spec.message;

import com.zhouruojun.agentcore.spec.Nullable;
import com.zhouruojun.agentcore.spec.error.JsonRpcError;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * <p>
 *     <a href="https://github.com/google/A2A/blob/main/docs/specification.md#7-protocol-rpc-methods">点击跳转</a>
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
