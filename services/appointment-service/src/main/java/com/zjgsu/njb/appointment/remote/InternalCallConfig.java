package com.zjgsu.njb.appointment.remote;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 跨服务调用的可信凭证（第 09 周）。
 *
 * <p><b>为什么需要：</b>pet-service 新增了「网关信任过滤器」，
 * 凡是 {@code /api/**} 的请求，必须带 {@code X-Trusted-Caller} 才放行。
 * 本服务用 OpenFeign 调 {@code GET /api/pets/{id}}，这种调用不经过网关，
 * 自然没有这个头 —— 不补上就会被当成「绕过网关的直连」拒掉（401），
 * 表现为创建预约时宠物校验失败。</p>
 *
 * <p><b>为什么用拦截器而不是每个接口手写头：</b>Feign 的
 * {@link RequestInterceptor} 对所有 @FeignClient 生效，以后新增调用方
 * 不用记得补头；写在方法参数里迟早会漏一个。</p>
 *
 * <p>凭证值与网关的 trusted-caller 相同，由环境变量注入。
 * 这是「服务间互信」的凭证，不是最终用户身份 —— 用户身份仍由网关写入的
 * X-Auth-* 承载，两者不要混用。</p>
 */
@Configuration
public class InternalCallConfig {

    @Bean
    public RequestInterceptor trustedCallerInterceptor(
            @Value("${app.security.trusted-caller:dev-only-trusted-caller-change-me}") String trustedCaller) {
        return template -> template.header("X-Trusted-Caller", trustedCaller);
    }
}
