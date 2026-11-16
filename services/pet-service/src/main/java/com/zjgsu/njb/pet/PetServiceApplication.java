package com.zjgsu.njb.pet;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * pet-service 启动类 —— 宠物档案 + 用户认证微服务。
 *
 * <p>基础包名 com.zjgsu.njb.pet（com.zjgsu.njb 为姓名缩写 njb 的根包）。
 * 本服务是宠物数据的唯一权威来源：petclinic_pet 库、pet 表，只读写自己的数据。</p>
 *
 * <p><b>第 09 周追加：</b>认证功能放在本服务（用户与宠物档案同属「用户侧」数据），
 * 提供 {@code POST /api/auth/login} 签发 JWT，账号表 sys_user 与 pet 表同库。
 * 网关只验签不签发，因此令牌的私钥只在本服务持有。</p>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class PetServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PetServiceApplication.class, args);
    }
}
