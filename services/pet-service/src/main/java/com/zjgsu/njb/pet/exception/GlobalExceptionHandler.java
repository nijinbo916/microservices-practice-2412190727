package com.zjgsu.njb.pet.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/**
 * 全局异常处理器。
 *
 * <pre>
 * ResourceNotFoundException -&gt; 404
 * 业务规则 / 参数校验失败    -&gt; 409 / 400
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

    /**
     * 登录失败 → 401。
     *
     * <p>错误码固定 {@code INVALID_CREDENTIALS}，消息固定「用户名或密码错误」，
     * 不区分是账号不存在还是密码错 —— 否则等于送攻击者一个用户名枚举接口。</p>
     */
    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiError> handleInvalidCredentials(InvalidCredentialsException ex,
                                                             HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiError.of(401, "INVALID_CREDENTIALS", ex.getMessage(), request.getRequestURI()));
    }

    /** 业务规则冲突 → 409。 */
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

    /**
     * 主动抛出的状态码异常（如 /api/auth/me 缺头 → 401、管理端角色不足 → 403）。
     *
     * <p><b>必须显式处理：</b>下面的兜底 {@code @ExceptionHandler(Exception.class)}
     * 会连 ResponseStatusException 一起吞掉，把 401/403 变成 500。
     * 这是 Spring 里很容易踩的一个坑 —— 兜底处理器优先级「太高」。</p>
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> handleResponseStatus(ResponseStatusException ex,
                                                         HttpServletRequest request) {
        int status = ex.getStatusCode().value();
        String code = switch (status) {
            case 401 -> "UNAUTHORIZED";
            case 403 -> "FORBIDDEN";
            case 404 -> "NOT_FOUND";
            default -> "REQUEST_REJECTED";
        };
        return ResponseEntity.status(status)
                .body(ApiError.of(status, code, ex.getReason(), request.getRequestURI()));
    }

    /** 兜底：未预期的异常 → 500。 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of(500, "INTERNAL_ERROR", ex.getMessage(), request.getRequestURI()));
    }
}
