package com.zhouruojun.a2acore.spec.message;

import com.zhouruojun.a2acore.spec.UpdateEvent;
import com.zhouruojun.a2acore.spec.error.JsonRpcError;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * <p>
 *     tasks/sendSubscribe
 *     <a href="https://github.com/google/A2A/blob/main/docs/specification.md#72-taskssendsubscribe"></a>
 * </p>
 *
 */
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@Data
public class SendTaskStreamingResponse extends JsonRpcResponse<UpdateEvent> {
    public SendTaskStreamingResponse() {
    }

    public SendTaskStreamingResponse(String id, UpdateEvent updateEvent) {
        this.setId(id);
        this.setResult(updateEvent);
    }

    public SendTaskStreamingResponse(String id, JsonRpcError error) {
        super(id, error);
    }
}


