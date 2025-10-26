package com.zhouruojun.amadeusmcp.exception;

/**
 * Amadeus MCP自定义异常
 */
public class AmadeusMCPException extends RuntimeException {
    
    private final String errorCode;
    private final String userMessage;
    
    public AmadeusMCPException(String message) {
        super(message);
        this.errorCode = "AMADEUS_MCP_ERROR";
        this.userMessage = message;
    }
    
    public AmadeusMCPException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.userMessage = message;
    }
    
    public AmadeusMCPException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.userMessage = message;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
    
    public String getUserMessage() {
        return userMessage;
    }
}
