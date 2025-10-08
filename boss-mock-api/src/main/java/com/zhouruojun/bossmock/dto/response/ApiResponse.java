package com.zhouruojun.bossmock.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


/**
 * 统一API响应格式
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    /**
     * 响应码
     */
    private Integer code;

    /**
     * 响应消息
     */
    private String message;

    /**
     * 响应数据
     */
    private T data;

    /**
     * 时间戳
     */
    @Builder.Default
    private Long timestamp = System.currentTimeMillis();

    /**
     * 请求路径
     */
    private String path;

    /**
     * 成功响应
     */
    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .code(200)
                .message("操作成功")
                .data(data)
                .build();
    }

    /**
     * 成功响应(带消息)
     */
    public static <T> ApiResponse<T> success(T data, String message) {
        return ApiResponse.<T>builder()
                .code(200)
                .message(message)
                .data(data)
                .build();
    }

    /**
     * 成功响应(无数据)
     */
    public static <T> ApiResponse<T> success() {
        return ApiResponse.<T>builder()
                .code(200)
                .message("操作成功")
                .build();
    }

    /**
     * 错误响应
     */
    public static <T> ApiResponse<T> error(String message) {
        return ApiResponse.<T>builder()
                .code(400)
                .message(message)
                .build();
    }

    /**
     * 错误响应(带错误码)
     */
    public static <T> ApiResponse<T> error(Integer code, String message) {
        return ApiResponse.<T>builder()
                .code(code)
                .message(message)
                .build();
    }

    /**
     * 未授权响应
     */
    public static <T> ApiResponse<T> unauthorized(String message) {
        return ApiResponse.<T>builder()
                .code(401)
                .message(message)
                .build();
    }

    /**
     * 禁止访问响应
     */
    public static <T> ApiResponse<T> forbidden(String message) {
        return ApiResponse.<T>builder()
                .code(403)
                .message(message)
                .build();
    }

    /**
     * 资源未找到响应
     */
    public static <T> ApiResponse<T> notFound(String message) {
        return ApiResponse.<T>builder()
                .code(404)
                .message(message)
                .build();
    }

    /**
     * 服务器错误响应
     */
    public static <T> ApiResponse<T> serverError(String message) {
        return ApiResponse.<T>builder()
                .code(500)
                .message(message)
                .build();
    }


    /**
     * 检查是否成功
     */
    public boolean isSuccess() {
        return code != null && code == 200;
    }

    /**
     * 检查是否失败
     */
    public boolean isError() {
        return !isSuccess();
    }
}

