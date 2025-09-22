package com.zhouruojun.agentcore.spec.error;


public class ServerError extends JsonRpcError {
    public ServerError(int code) {
        this.setCode(code);
        this.setMessage("Server Error");
    }
}
