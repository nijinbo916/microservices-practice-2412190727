package com.zjgsu.njb.doctor.exception;

/** 资源不存在异常，由全局异常处理器映射为 404。 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
