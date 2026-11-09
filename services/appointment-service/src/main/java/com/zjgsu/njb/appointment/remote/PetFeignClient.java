package com.zjgsu.njb.appointment.remote;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * pet-service 的 OpenFeign 声明式客户端（第 08 周）。
 *
 * <p><b>按服务名调用，不写死容器 IP：</b>name = "pet-service" 与对方
 * spring.application.name 一致；实际请求哪个实例由 Spring Cloud LoadBalancer
 * 从 Nacos 的实例列表中选择 —— pet-service 多实例运行时自动分担流量。</p>
 *
 * <p>接口方法与 pet-service 的真实接口一一对应：
 * {@code GET /api/pets/{id}} 返回 200 + 宠物 JSON，或 404。</p>
 *
 * <p>失败处理不在接口层做：非 2xx 响应由 Feign 抛出 {@link feign.FeignException}，
 * 在 {@link PetClient} 门面统一转换成 404 / 503 业务异常。</p>
 */
@FeignClient(name = "${pet.service-name:pet-service}", path = "/api")
public interface PetFeignClient {

    /** 按 id 查询宠物：200（JSON）或 404（宠物不存在）。 */
    @GetMapping("/pets/{id}")
    PetView getPet(@PathVariable("id") Long id);
}
