package com.zhouruojun.amadeusmcp.exception;

import com.amadeus.exceptions.ResponseException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * 全局异常处理器
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * 处理Amadeus MCP自定义异常
     */
    @ExceptionHandler(AmadeusMCPException.class)
    public ResponseEntity<Map<String, Object>> handleAmadeusMCPException(AmadeusMCPException e) {
        log.error("Amadeus MCP异常: {}", e.getMessage(), e);
        
        return ResponseEntity.badRequest()
                .body(Map.of(
                    "status", "error",
                    "errorCode", e.getErrorCode(),
                    "message", e.getUserMessage(),
                    "timestamp", System.currentTimeMillis()
                ));
    }

    /**
     * 处理Amadeus API异常
     */
    @ExceptionHandler(ResponseException.class)
    public ResponseEntity<Map<String, Object>> handleAmadeusResponseException(ResponseException e) {
        log.error("Amadeus API异常: {}", e.getMessage(), e);
        
        return ResponseEntity.badRequest()
                .body(Map.of(
                    "status", "error",
                    "errorCode", "AMADEUS_API_ERROR",
                    "message", "Amadeus API调用失败: " + e.getMessage(),
                    "timestamp", System.currentTimeMillis()
                ));
    }

    /**
     * 处理参数异常
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgumentException(IllegalArgumentException e) {
        log.error("参数异常: {}", e.getMessage(), e);
        
        return ResponseEntity.badRequest()
                .body(Map.of(
                    "status", "error",
                    "errorCode", "INVALID_PARAMETERS",
                    "message", "参数错误: " + e.getMessage(),
                    "timestamp", System.currentTimeMillis()
                ));
    }

    /**
     * 处理其他异常
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception e) {
        log.error("未知异常: {}", e.getMessage(), e);
        
        return ResponseEntity.internalServerError()
                .body(Map.of(
                    "status", "error",
                    "errorCode", "INTERNAL_ERROR",
                    "message", "服务器内部错误: " + e.getMessage(),
                    "timestamp", System.currentTimeMillis()
                ));
    }
}
