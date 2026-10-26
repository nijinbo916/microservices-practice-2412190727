package com.zjgsu.njb.pet.exception;

/**
 * 统一的 API 错误响应体。
 */
public record ApiError(
        java.time.LocalDateTime timestamp,
        int status,
        String error,
        String message,
        String path
) {

    public static ApiError of(int status, String error, String message, String path) {
        return new ApiError(java.time.LocalDateTime.now(), status, error, message, path);
    }
}
