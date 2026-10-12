package com.zjgsu.njb.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器：把各类异常统一转换为规范的 HTTP 状态码与 JSON 错误体。
 *
 * <pre>
 * ResourceNotFoundException  -> 404 Not Found
 * BusinessRuleException      -> 409 Conflict
 * 参数校验失败               -> 400 Bad Request
 * </pre>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 资源不存在 → 404。 */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException ex,
                                                   HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of(404, "NOT_FOUND", ex.getMessage(), request.getRequestURI()));
    }

    /** 业务规则冲突（如号源冲突、超出每日号源上限）→ 409。 */
    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ApiError> handleBusinessRule(BusinessRuleException ex,
                                                       HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of(409, "BUSINESS_RULE_VIOLATION", ex.getMessage(), request.getRequestURI()));
    }

    /** 请求体参数校验失败 → 400，逐条列出字段错误。 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex,
                                                     HttpServletRequest request) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest()
                .body(ApiError.of(400, "VALIDATION_FAILED", detail, request.getRequestURI()));
    }

    /** 其余非法参数 → 400。 */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException ex,
                                                          HttpServletRequest request) {
        return ResponseEntity.badRequest()
                .body(ApiError.of(400, "BAD_REQUEST", ex.getMessage(), request.getRequestURI()));
    }

    /** 兜底：未预期的异常 → 500。 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of(500, "INTERNAL_ERROR", ex.getMessage(), request.getRequestURI()));
    }
}
