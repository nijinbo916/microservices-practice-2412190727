package com.zjgsu.njb.appointment.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器。
 *
 * <pre>
 * ResourceNotFoundException         -&gt; 404（含远端校验 404）
 * BusinessRuleException            -&gt; 409
 * DependencyUnavailableException   -&gt; 503（依赖服务不可达/超时/5xx）
 * 参数校验失败 / 非法参数           -&gt; 400
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

    /** 业务规则冲突 → 409。 */
    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ApiError> handleBusinessRule(BusinessRuleException ex,
                                                       HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of(409, "BUSINESS_RULE_VIOLATION", ex.getMessage(), request.getRequestURI()));
    }

    /** 依赖服务不可用 → 503，客户端应稍后重试；本地数据未写入。 */
    @ExceptionHandler(DependencyUnavailableException.class)
    public ResponseEntity<ApiError> handleDependencyUnavailable(DependencyUnavailableException ex,
                                                                HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiError.of(503, "DEPENDENCY_UNAVAILABLE", ex.getMessage(), request.getRequestURI()));
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
