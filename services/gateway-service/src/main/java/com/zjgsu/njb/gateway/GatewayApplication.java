package com.zjgsu.njb.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * gateway-service 启动类 —— 系统唯一对外入口（第 09 周）。
 *
 * <p>职责只有两件事：</p>
 * <ol>
 *   <li><b>路由转发</b>：按路径把请求转给 pet-service / doctor-service /
 *       appointment-service，目标地址写服务名（{@code lb://pet-service}），
 *       具体实例由 Nacos + Spring Cloud LoadBalancer 决定，网关不保存任何 IP。</li>
 *   <li><b>统一鉴权</b>：校验 Authorization: Bearer 里的 JWT，验签通过才放行，
 *       并把身份写进内部请求头；校验失败直接返回 401 / 403，请求不会到达任何业务服务。</li>
 * </ol>
 *
 * <p><b>必须运行在 REACTIVE 模式。</b>Spring Cloud Gateway 5.x 的
 * {@code spring-cloud-starter-gateway-server-webflux} 依赖 WebFlux，
 * 一旦 classpath 上出现 spring-boot-starter-web（Servlet），网关会在 Servlet
 * 模式下启动，路由基础设施不装配、{@code /actuator/gateway/routes} 返回空数组。
 * 因此本工程刻意不引入 web starter。</p>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
