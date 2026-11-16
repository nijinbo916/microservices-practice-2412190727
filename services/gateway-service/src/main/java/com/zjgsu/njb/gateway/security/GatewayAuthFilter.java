package com.zjgsu.njb.gateway.security;

import com.zjgsu.njb.gateway.config.GatewaySecurityProperties;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 全局鉴权过滤器 —— 系统的唯一闸门。
 *
 * <p>处理顺序（每一步都不能省）：</p>
 * <ol>
 *   <li><b>先删客户端自带的身份头</b>：{@code X-Auth-User}、{@code X-Auth-Roles}、
 *       {@code X-Auth-Uid}、{@code X-Trusted-Caller}。这一步必须在验签之前做，
 *       否则客户端写个 {@code X-Auth-Roles: ADMIN} 就能冒充管理员。</li>
 *   <li>公开入口（登录、健康检查）直接放行，但仍带着「已清理」的头放行。</li>
 *   <li>其余请求必须有 {@code Authorization: Bearer <token>}，验签失败 → 401。</li>
 *   <li>验签通过后再查角色规则，角色不够 → 403。</li>
 *   <li>放行前把<b>可信身份</b>写回请求头，供下游展示/审计使用。</li>
 * </ol>
 *
 * <p>401 与 403 的区别就是作业要求的两种拦截：没登录是 401，
 * 登录了但越权是 403 —— 两者都在网关返回，请求不会打到下游。</p>
 */
@Component
public class GatewayAuthFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(GatewayAuthFilter.class);

    /** 必须被网关重写的头：客户端带了也不算数。 */
    private static final List<String> SPOOFABLE_HEADERS = List.of(
            "X-Auth-User", "X-Auth-Uid", "X-Auth-Roles", "X-Trusted-Caller"
    );

    private final JwtVerifier jwtVerifier;
    private final RolePolicy rolePolicy;
    private final String trustedCaller;

    public GatewayAuthFilter(JwtVerifier jwtVerifier,
                             RolePolicy rolePolicy,
                             GatewaySecurityProperties properties) {
        this.jwtVerifier = jwtVerifier;
        this.rolePolicy = rolePolicy;
        this.trustedCaller = properties.trustedCaller();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest incoming = exchange.getRequest();
        String path = incoming.getURI().getPath();
        String method = incoming.getMethod().name();

        // 步骤 1：无论什么请求，先剥掉可伪造的身份头
        ServerHttpRequest cleaned = incoming.mutate()
                .headers(headers -> SPOOFABLE_HEADERS.forEach(headers::remove))
                .build();

        // 步骤 2：公开入口
        if (rolePolicy.isPublic(path)) {
            return chain.filter(exchange.mutate().request(cleaned).build());
        }

        // 步骤 3：取 token 并验签
        String token = extractBearer(cleaned.getHeaders());
        if (token == null) {
            log.info("401 未携带 token：{} {}", method, path);
            return reject(exchange, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED",
                    "缺少 Authorization: Bearer <token>");
        }
        Optional<JwtVerifier.AuthenticatedUser> verified = jwtVerifier.verify(token);
        if (verified.isEmpty()) {
            log.info("401 token 无效或已过期：{} {}", method, path);
            return reject(exchange, HttpStatus.UNAUTHORIZED, "INVALID_TOKEN",
                    "token 无效、签名不符或已过期");
        }

        // 步骤 4：角色校验
        JwtVerifier.AuthenticatedUser user = verified.get();
        String denial = rolePolicy.check(method, path, user.roles());
        if (denial != null) {
            log.info("403 越权：user={} roles={} {} {}", user.username(), user.roles(), method, path);
            return reject(exchange, HttpStatus.FORBIDDEN, "FORBIDDEN",
                    denial);
        }

        // 步骤 5：注入可信身份 + 网关凭证
        ServerHttpRequest authenticated = cleaned.mutate()
                .header("X-Auth-User", user.username())
                .header("X-Auth-Roles", String.join(",", user.roles()))
                .header("X-Auth-Uid", user.userId() == null ? "" : String.valueOf(user.userId()))
                .header("X-Trusted-Caller", trustedCaller)
                .build();

        log.debug("放行：user={} roles={} {} {}", user.username(), user.roles(), method, path);
        return chain.filter(exchange.mutate().request(authenticated).build());
    }

    /** 过滤器顺序：越靠前越先执行，鉴权必须在路由转发之前。 */
    @Override
    public int getOrder() {
        return -100;
    }

    private String extractBearer(HttpHeaders headers) {
        String authorization = headers.getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization == null) {
            return null;
        }
        if (!authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return null;
        }
        String token = authorization.substring(7).trim();
        return token.isEmpty() ? null : token;
    }

    /**
     * 直接返回错误响应，不继续往下传 —— 下游服务不会看到这次请求。
     * 响应体结构与业务服务的 ApiError 保持一致（timestamp/status/error/message/path），
     * 前端不用为「网关错误」和「业务错误」写两套解析。
     */
    private Mono<Void> reject(ServerWebExchange exchange,
                              HttpStatus status,
                              String errorCode,
                              String message) {
        String body = """
                {"timestamp":"%s","status":%d,"error":"%s","message":"%s","path":"%s"}"""
                .formatted(
                        java.time.OffsetDateTime.now(),
                        status.value(),
                        errorCode,
                        message.replace("\"", "'"),
                        exchange.getRequest().getURI().getRawPath());
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        DataBuffer buffer = exchange.getResponse()
                .bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8));
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}
