package com.zjgsu.njb.appointment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * appointment-service 启动类 —— 预约微服务（本周核心）。
 *
 * <p>基础包名 com.zjgsu.njb.appointment。独占 petclinic_appointment 库与 appointment 表；
 * 本服务不持有宠物、医生数据，创建预约前必须通过 OpenFeign 按服务名调用
 * pet-service / doctor-service 完成校验，跨服务关联只保存对方的 ID。</p>
 *
 * <p><b>第 08 周：跨服务 HTTP 调用从「RestTemplate + 手写寻址」升级为 OpenFeign 声明式客户端。</b>
 * {@code @EnableFeignClients} 扫描 remote 包下的 @FeignClient 接口，
 * 请求按服务名（pet-service / doctor-service）发出，底层由 Spring Cloud LoadBalancer
 * 结合 Nacos 的实例列表选择具体实例，多实例时自动分担流量。</p>
 */
@EnableFeignClients(basePackages = "com.zjgsu.njb.appointment.remote")
@SpringBootApplication
public class AppointmentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AppointmentServiceApplication.class, args);
    }
}
