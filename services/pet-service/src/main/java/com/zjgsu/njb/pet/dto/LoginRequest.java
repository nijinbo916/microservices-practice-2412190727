package com.zjgsu.njb.pet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 登录请求体。
 *
 * <p>密码上限 72 字节不是随便定的：BCrypt 只处理前 72 字节，
 * 超长密码会被静默截断，导致「输错后面几位也能登录」。
 * 这里直接卡住，比让用户以为自己设了超长密码更安全。</p>
 */
public record LoginRequest(
        @NotBlank(message = "用户名不能为空")
        @Size(max = 50, message = "用户名最长 50 个字符")
        String username,

        @NotBlank(message = "密码不能为空")
        @Size(max = 72, message = "密码最长 72 个字符")
        String password
) {
}
