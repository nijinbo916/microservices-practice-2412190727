package com.zjgsu.njb.appointment.exception;

/** 依赖服务不可用异常（远端不可达 / 超时 / 5xx），映射为 503，阻止本地写入。 */
public class DependencyUnavailableException extends RuntimeException {

    public DependencyUnavailableException(String message) {
        super(message);
    }
}
