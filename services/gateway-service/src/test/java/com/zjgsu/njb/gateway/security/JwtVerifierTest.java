package com.zjgsu.njb.gateway.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zjgsu.njb.gateway.config.GatewaySecurityProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 验签器单测：只测网关最关键的几个判断，不启动 Spring 上下文。
 *
 * <p>覆盖：合法 token 能拿到身份与角色、过期 token 被拒、
 * 别的密钥签的 token 被拒、iss 不对被拒。</p>
 */
class JwtVerifierTest {

    private static final String SECRET = "unit-test-secret-0123456789-abcdefghij";
    private static final String ISSUER = "petclinic-auth";

    private final SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    private final JwtVerifier verifier = new JwtVerifier(
            new GatewaySecurityProperties(SECRET, ISSUER, "trusted"));

    private String token(String subject, List<String> roles, String issuer, Instant expiry, SecretKey signingKey) {
        return Jwts.builder()
                .subject(subject)
                .claim("uid", 7)
                .claim("roles", roles)
                .issuer(issuer)
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(expiry))
                .signWith(signingKey)
                .compact();
    }

    @Test
    @DisplayName("合法 token：解析出用户名、uid 和角色")
    void acceptsValidToken() {
        String jwt = token("admin", List.of("ADMIN"), ISSUER, Instant.now().plusSeconds(600), key);

        Optional<JwtVerifier.AuthenticatedUser> user = verifier.verify(jwt);

        assertTrue(user.isPresent());
        assertEquals("admin", user.get().username());
        assertEquals(7L, user.get().userId());
        assertTrue(user.get().hasAnyRole("ADMIN"));
    }

    @Test
    @DisplayName("过期 token：验签失败，返回空")
    void rejectsExpiredToken() {
        String jwt = token("admin", List.of("ADMIN"), ISSUER, Instant.now().minusSeconds(60), key);
        assertTrue(verifier.verify(jwt).isEmpty());
    }

    @Test
    @DisplayName("别的密钥签的 token：签名不符，返回空")
    void rejectsTokenSignedByAnotherKey() {
        SecretKey attackerKey = Keys.hmacShaKeyFor(
                "attacker-secret-0123456789-abcdefghij".getBytes(StandardCharsets.UTF_8));
        String forged = token("admin", List.of("ADMIN"), ISSUER, Instant.now().plusSeconds(600), attackerKey);

        assertTrue(verifier.verify(forged).isEmpty());
    }

    @Test
    @DisplayName("签发者不对：即使签名对也拒掉")
    void rejectsWrongIssuer() {
        String jwt = token("admin", List.of("ADMIN"), "other-system", Instant.now().plusSeconds(600), key);
        assertTrue(verifier.verify(jwt).isEmpty());
    }

    @Test
    @DisplayName("垃圾字符串：不抛异常，返回空")
    void rejectsGarbage() {
        assertTrue(verifier.verify("not-a-jwt").isEmpty());
    }
}
