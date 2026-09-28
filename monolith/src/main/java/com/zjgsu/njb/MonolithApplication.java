package com.zjgsu.njb;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 萌宠诊所 · 宠物医院管理系统 —— 单体应用启动类。
 *
 * <p>第 03 周目标：应用可启动、基础配置就绪、提供用于验证运行状态的 GET 接口。</p>
 */
@SpringBootApplication
public class MonolithApplication {

    public static void main(String[] args) {
        SpringApplication.run(MonolithApplication.class, args);
    }

}
