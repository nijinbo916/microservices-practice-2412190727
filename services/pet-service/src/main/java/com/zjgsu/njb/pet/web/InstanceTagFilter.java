package com.zjgsu.njb.pet.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.InetAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 实例标识过滤器（第 08 周 · 负载均衡验证用）。
 *
 * <p>多实例运行时（{@code docker compose up -d --scale pet-service=2}），
 * 每个实例的容器 ID 不同。本过滤器做两件事，给「调用方确实访问到了两个实例」提供可核验证据：</p>
 *
 * <ol>
 *   <li>给每个响应加 {@code X-Instance-Id} 响应头（值为容器 hostname / 容器 ID）；</li>
 *   <li>每个请求打一行 {@code [LB]} 日志，格式固定，方便 {@code docker compose logs pet-service} 后
 *       grep 出两个不同的实例标识。</li>
 * </ol>
 *
 * <p>注意：业务逻辑不受影响，本过滤器只读 hostname，不改任何请求/响应内容。</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class InstanceTagFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(InstanceTagFilter.class);

    /** 实例标识：容器内 HOSTNAME 即容器 ID；容器外运行时回退到机器名。 */
    private final String instanceId;

    public InstanceTagFilter() {
        String host = System.getenv("HOSTNAME");
        if (host == null || host.isBlank()) {
            try {
                host = InetAddress.getLocalHost().getHostName();
            } catch (Exception e) {
                host = "unknown-instance";
            }
        }
        this.instanceId = host;
        log.info("[LB] pet-service 实例启动，instance-id={}", instanceId);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        response.setHeader("X-Instance-Id", instanceId);
        log.info("[LB] instance={} served {} {}", instanceId, request.getMethod(), request.getRequestURI());
        filterChain.doFilter(request, response);
    }
}
