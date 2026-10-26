package com.zjgsu.njb.appointment.exception;

/** 资源不存在异常，映射为 404。 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
