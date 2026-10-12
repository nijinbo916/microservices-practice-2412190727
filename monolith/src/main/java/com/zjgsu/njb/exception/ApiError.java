package com.zjgsu.njb.exception;

import java.time.LocalDateTime;

/**
 * 统一的 API 错误响应体。
 */
public record ApiError(
        LocalDateTime timestamp,
        int status,
        String error,
        String message,
        String path
) {

    public static ApiError of(int status, String error, String message, String path) {
        return new ApiError(LocalDateTime.now(), status, error, message, path);
    }
}
