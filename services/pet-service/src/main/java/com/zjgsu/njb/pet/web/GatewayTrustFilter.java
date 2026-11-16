package com.zjgsu.njb.pet.web;

import com.zjgsu.njb.pet.security.AuthProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 网关信任过滤器 —— 防止绕过网关直连本服务。
 *
 * <p><b>要解决的问题：</b>业务服务的端口如果对外可达，攻击者可以直接
 * {@code curl http://host:8081/api/pets}，此时根本不过网关，
 * 也就不会有 JWT 校验。光靠网关鉴权等于没鉴权。</p>
 *
 * <p><b>两道防线：</b></p>
 * <ol>
 *   <li><b>网络层</b>（compose.yaml）：业务服务端口只绑 {@code 127.0.0.1}，
 *       外部访问不到；网关是唯一发布到 0.0.0.0 的服务。</li>
 *   <li><b>应用层</b>（本过滤器）：即使是同机/同网络的请求，
 *       没有正确的 {@code X-Trusted-Caller} 也一律 401。
 *       这个头由网关在校验 JWT 通过后写入，客户端自带的会被网关先删掉。</li>
 * </ol>
 *
 * <p>白名单只留登录和健康检查：登录时用户还没有 token，不可能带网关凭证；
 * 健康检查是容器探针直连，不能要求它经过网关。</p>
 */
@Component
@Order(1)
public class GatewayTrustFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(GatewayTrustFilter.class);

    private static final String TRUST_HEADER = "X-Trusted-Caller";

    /** 必须绕过信任校验的路径（登录还没有 token，探针不经过网关）。 */
    private static final List<String> EXEMPT = List.of(
            "/api/auth/login",
            "/actuator/health",
            "/actuator/health/**"
    );

    private final AntPathMatcher matcher = new AntPathMatcher();
    private final AuthProperties properties;

    public GatewayTrustFilter(AuthProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();

        if (!properties.trustEnabled()
                || !path.startsWith("/api/")
                || EXEMPT.stream().anyMatch(p -> matcher.match(p, path))) {
            chain.doFilter(request, response);
            return;
        }

        String caller = request.getHeader(TRUST_HEADER);
        if (caller == null || !caller.equals(properties.trustedCaller())) {
            log.warn("拒绝直连访问：{} {} 来自 {}（缺少或错误的 {}）",
                    request.getMethod(), path, request.getRemoteAddr(), TRUST_HEADER);
            writeUnauthorized(response, path);
            return;
        }

        chain.doFilter(request, response);
    }

    /** 返回与网关一致的结构（timestamp/status/error/message/path），便于前端统一处理。 */
    private void writeUnauthorized(HttpServletResponse response, String path) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        String body = """
                {"timestamp":"%s","status":401,"error":"DIRECT_ACCESS_DENIED",\
                "message":"请求必须经过 API Gateway（缺少可信网关凭证）","path":"%s"}"""
                .formatted(java.time.OffsetDateTime.now(), path);
        response.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
    }
}
