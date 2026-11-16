package com.zjgsu.njb.pet.dto;

import com.zjgsu.njb.pet.domain.UserAccount;
import com.zjgsu.njb.pet.security.JwtIssuer;
import java.time.Instant;

/**
 * 登录成功的响应。
 *
 * <p>字段名沿用 OAuth2 / RFC 6750 的习惯（access_token、token_type、expires_in），
 * 前端或 curl 不用记自定义名字。这里用 camelCase 输出，
 * 与本项目其他接口的 JSON 风格保持一致。</p>
 *
 * @param accessToken  JWT 本体
 * @param tokenType    固定 Bearer，放在 Authorization 头里
 * @param expiresIn    有效期（秒），前端可以据此提前刷新
 * @param expiresAt    Token 绝对过期时间，便于调试
 * @param username     登录名
 * @param displayName  展示名
 * @param role         角色（ADMIN / DOCTOR / OWNER）
 */
public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        Instant expiresAt,
        String username,
        String displayName,
        String role
) {

    public static LoginResponse of(UserAccount account, JwtIssuer.IssuedToken token) {
        return new LoginResponse(
                token.token(),
                "Bearer",
                token.ttlSeconds(),
                token.expiresAt(),
                account.getUsername(),
                account.getDisplayName(),
                account.getRole().name()
        );
    }
}
