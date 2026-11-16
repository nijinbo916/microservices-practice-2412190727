package com.zjgsu.njb.pet.security;

import java.nio.charset.StandardCharsets;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 认证与网关信任配置（第 09 周）。
 *
 * <p>所有密钥都从环境变量来，仓库里只有占位符。{@code jwtSecret} 与网关的
 * {@code app.security.jwt-secret} 必须完全一致，否则网关验签必失败。</p>
 *
 * @param jwtSecret      HS256 密钥，至少 32 字节
 * @param issuer         JWT 签发者，写进 iss，网关会强校验
 * @param ttlMinutes     token 有效期（分钟），默认 60
 * @param seedPassword   初始化测试账号时用的密码，只在库为空时写入一次；
 *                       真实部署应通过环境变量覆盖，不依赖这个默认值
 * @param trustedCaller  网关共享密钥，与网关的 trusted-caller 一致
 * @param trustEnabled   是否强制要求「请求必须来自网关」；
 *                       自动测试（MockMvc）直连控制器，测试环境把它关掉
 */
@ConfigurationProperties(prefix = "app.security")
public record AuthProperties(
        String jwtSecret,
        String issuer,
        long ttlMinutes,
        String seedPassword,
        String trustedCaller,
        boolean trustEnabled
) {

    public AuthProperties {
        if (jwtSecret == null
                || jwtSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "app.security.jwt-secret 未配置或不足 32 字节，拒绝启动（与网关必须一致）");
        }
        if (issuer == null || issuer.isBlank()) {
            issuer = "petclinic-auth";
        }
        if (ttlMinutes <= 0) {
            ttlMinutes = 60;
        }
        if (seedPassword == null || seedPassword.isBlank()) {
            seedPassword = "ChangeMe@2026";
        }
        if (trustedCaller == null || trustedCaller.isBlank()) {
            throw new IllegalStateException("app.security.trusted-caller 未配置");
        }
    }
}
