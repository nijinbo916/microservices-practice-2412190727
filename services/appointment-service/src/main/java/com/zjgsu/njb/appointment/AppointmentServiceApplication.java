package com.zjgsu.njb.appointment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * appointment-service 启动类 —— 预约微服务（本周核心）。
 *
 * <p>基础包名 com.zjgsu.njb.appointment。独占 petclinic_appointment 库与 appointment 表；
 * 本服务不持有宠物、医生数据，创建预约前必须通过 RestTemplate 调用
 * pet-service / doctor-service 完成校验，跨服务关联只保存对方的 ID。</p>
 */
@SpringBootApplication
public class AppointmentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AppointmentServiceApplication.class, args);
    }
}
