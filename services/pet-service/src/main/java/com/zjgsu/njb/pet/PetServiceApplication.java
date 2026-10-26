package com.zjgsu.njb.pet;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * pet-service 启动类 —— 宠物档案微服务。
 *
 * <p>基础包名 com.zjgsu.njb.pet（com.zjgsu.njb 为姓名缩写 njb 的根包）。
 * 本服务是宠物数据的唯一权威来源：petclinic_pet 库、pet 表，只读写自己的数据。</p>
 */
@SpringBootApplication
public class PetServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PetServiceApplication.class, args);
    }
}
