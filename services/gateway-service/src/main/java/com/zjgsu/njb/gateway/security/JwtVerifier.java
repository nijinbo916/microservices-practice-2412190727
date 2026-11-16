package com.zjgsu.njb.gateway.security;

import com.zjgsu.njb.gateway.config.GatewaySecurityProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * JWT 验签器：网关只验签，不签发。
 *
 * <p>签发在 pet-service（认证服务）完成，双方共用同一个 HS256 密钥；
 * 网关拿到 token 后校验三件事：</p>
 * <ol>
 *   <li>签名对不对（密钥错误、被篡改过 → 签名校验失败）</li>
 *   <li>签发者是不是自己人（iss 必须等于 app.security.issuer）</li>
 *   <li>有没有过期（exp 由 jjwt 自动校验，过期抛 ExpiredJwtException）</li>
 * </ol>
 *
 * <p>任何一项不通过都返回空 Optional，由调用方统一转成 401 ——
 * 不向客户端区分「签名错」还是「过期」，避免给攻击者探测信息。</p>
 */
@Component
public class JwtVerifier {

    private static final Logger log = LoggerFactory.getLogger(JwtVerifier.class);

    private final SecretKey key;
    private final String issuer;

    public JwtVerifier(GatewaySecurityProperties properties) {
        this.key = Keys.hmacShaKeyFor(properties.jwtSecret().getBytes(StandardCharsets.UTF_8));
        this.issuer = properties.issuer();
    }

    /** 校验 token，成功返回调用者身份，失败返回空。 */
    public Optional<AuthenticatedUser> verify(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(issuer)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String username = claims.getSubject();
            if (username == null || username.isBlank()) {
                return Optional.empty();
            }
            Object uidRaw = claims.get("uid");
            Long uid = (uidRaw instanceof Number number) ? number.longValue() : null;
            List<String> roles = readRoles(claims);
            return Optional.of(new AuthenticatedUser(username, uid, roles));
        } catch (JwtException | IllegalArgumentException ex) {
            // 过期、签名不符、格式错误都走这里；只记类型，不打印 token 本体
            log.debug("JWT 校验失败：{}", ex.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> readRoles(Claims claims) {
        Object raw = claims.get("roles");
        if (raw instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        if (raw instanceof String text && !text.isBlank()) {
            return List.of(text.split(","));
        }
        return List.of();
    }

    /** 验签通过后的调用者身份。 */
    public record AuthenticatedUser(String username, Long userId, List<String> roles) {

        public boolean hasAnyRole(String... required) {
            for (String role : required) {
                if (roles.contains(role)) {
                    return true;
                }
            }
            return false;
        }
    }
}
