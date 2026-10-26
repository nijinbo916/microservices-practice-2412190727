package com.zjgsu.njb.doctor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * doctor-service 启动类 —— 医生主数据微服务。
 *
 * <p>基础包名 com.zjgsu.njb.doctor。本服务独占 petclinic_doctor 库与 doctor 表，
 * 对外提供医生查询与存在性校验；预约服务创建预约时通过 HTTP 来取医生信息与每日号源上限。</p>
 */
@SpringBootApplication
public class DoctorServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DoctorServiceApplication.class, args);
    }
}
