package com.zjgsu.njb.pet.exception;

/**
 * 登录失败（用户名不存在、密码错误、账号停用）统一抛这个。
 *
 * <p>三种情况都返回同一个提示「用户名或密码错误」，不告诉调用方到底哪一步错了 ——
 * 否则攻击者可以用它来枚举系统里存在哪些用户名。</p>
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException(String message) {
        super(message);
    }
}
