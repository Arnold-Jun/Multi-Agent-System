package com.zhouruojun.agentcore.spec.error;

import lombok.Getter;

/**
 * <p>
 * </p>
 *
 */
@Getter
public class A2AClientJSONError extends A2AClientError {
    private String message;

    public A2AClientJSONError(String message) {
        super(String.format("JSON Error: %s", message));
        this.message = message;
    }

}
