package com.zjgsu.njb.appointment.exception;

/** 业务规则冲突异常（号源冲突 / 每日上限 / 状态流转非法），映射为 409。 */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
