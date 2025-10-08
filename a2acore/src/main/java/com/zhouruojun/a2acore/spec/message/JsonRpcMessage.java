package com.zhouruojun.a2acore.spec.message;

import com.zhouruojun.a2acore.spec.message.util.Uuid;
import lombok.Data;
import lombok.ToString;

/**
 * <p>
 *     <a href="https://github.com/google/A2A/blob/main/docs/specification.md#7-protocol-rpc-methods"></a>
 * </p>
 *
 */
@ToString(callSuper = true)
@Data
public class JsonRpcMessage {
    protected final String jsonrpc = "2.0";
    /**
     * message id
     */
    protected String id;

    public JsonRpcMessage() {
        this.id = Uuid.uuid4hex();
    }
}


