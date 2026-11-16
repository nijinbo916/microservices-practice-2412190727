package com.zjgsu.njb.gateway.web;

import java.time.OffsetDateTime;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 网关自身健康检查入口。
 *
 * <p>作业要求「保留 /api/health 或 /actuator/health 检查入口」，
 * 这里两个都留：{@code /actuator/health} 是 Spring Boot 标准端点，
 * {@code /api/health} 是不带 actuator 依赖也能用的轻量探针，
 * 而且它在鉴权白名单里，运维不需要 token 就能探活。</p>
 *
 * <p>注意这里返回的是<b>网关自己</b>的状态，不是下游服务的聚合状态 ——
 * 下游是否可用由 /actuator/gateway/routes 与各服务的 /actuator/health 分别确认，
 * 避免一个服务挂掉就让整个健康检查变红、掩盖真实故障点。</p>
 */
@RestController
public class HealthController {

    @GetMapping("/api/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "service", "gateway-service",
                "timestamp", OffsetDateTime.now().toString()
        );
    }
}
