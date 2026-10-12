package com.zjgsu.njb.exception;

/**
 * 业务规则冲突异常，由全局异常处理器转换为 HTTP 409。
 *
 * <p>典型场景：同一医生同一时段已被预约（号源冲突）、当日号源已达上限、
 * 取消一条已完成的预约等。</p>
 */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
