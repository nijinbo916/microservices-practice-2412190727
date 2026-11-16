package com.zjgsu.njb.pet.security;

import com.zjgsu.njb.pet.domain.UserAccount;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

/**
 * JWT 签发器（只在认证服务里，网关没有这个类）。
 *
 * <p>令牌里只放三样东西：用户名（sub）、用户 id（uid）、角色（roles）。
 * 刻意不放手机号、身份证、宠物列表等业务数据 —— token 是 Base64 编码而非加密，
 * 任何人都能解开看内容，放得越多泄露越多。</p>
 *
 * <p>用 HS256 对称签名：签发方和验签方（网关）共享同一个密钥。
 * 选择对称而非 RS256 的原因是本项目只有一个签发者、一个验签者，
 * 对称密钥部署更简单；代价是密钥必须同时交给网关，
 * 所以真实环境必须走环境变量/KMS，绝不能写进代码或镜像。</p>
 */
@Component
public class JwtIssuer {

    private final SecretKey key;
    private final String issuer;
    private final long ttlSeconds;

    public JwtIssuer(AuthProperties properties) {
        this.key = Keys.hmacShaKeyFor(properties.jwtSecret().getBytes(StandardCharsets.UTF_8));
        this.issuer = properties.issuer();
        this.ttlSeconds = properties.ttlMinutes() * 60;
    }

    /** 为登录成功的账号签发 token。 */
    public IssuedToken issue(UserAccount account) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(ttlSeconds);
        List<String> roles = List.of(account.getRole().name());

        String token = Jwts.builder()
                .subject(account.getUsername())
                .claim("uid", account.getId())
                .claim("roles", roles)
                .issuer(issuer)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(key, Jwts.SIG.HS256)
                .compact();

        return new IssuedToken(token, expiresAt, ttlSeconds, roles);
    }

    /** 签发结果，供接口直接组装响应。 */
    public record IssuedToken(String token, Instant expiresAt, long ttlSeconds, List<String> roles) {
    }
}
