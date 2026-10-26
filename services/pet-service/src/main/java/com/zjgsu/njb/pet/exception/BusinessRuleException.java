package com.zjgsu.njb.pet.exception;

/** 业务规则冲突异常（本服务当前无强规则，保留以统一三服务的异常契约），映射为 409。 */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
