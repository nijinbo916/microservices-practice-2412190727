package com.zjgsu.njb.appointment.remote;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * doctor-service 的 OpenFeign 声明式客户端（第 08 周）。
 *
 * <p>name = "doctor-service" 与对方 spring.application.name 一致，
 * 地址由 Nacos + LoadBalancer 实时决定，不在任何配置里写容器 IP。</p>
 *
 * <p>除了校验医生存在，还取回 {@code dailyCapacity}（每日号源上限）
 * 供 appointment-service 的号源规则使用。</p>
 */
@FeignClient(name = "${doctor.service-name:doctor-service}", path = "/api")
public interface DoctorFeignClient {

    /** 按 id 查询医生：200（JSON，含 dailyCapacity）或 404（医生不存在）。 */
    @GetMapping("/doctors/{id}")
    DoctorView getDoctor(@PathVariable("id") Long id);
}
