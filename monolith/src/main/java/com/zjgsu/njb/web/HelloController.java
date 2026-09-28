package com.zjgsu.njb.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 运行状态验证接口。
 *
 * <p>本周只提供用于验证应用是否正常运行的问候接口，不涉及业务 CRUD。</p>
 */
@RestController
@RequestMapping("/api")
public class HelloController {

    @Value("${app.project.name}")
    private String projectName;

    /**
     * 问候接口：返回项目名称与问候消息，用于确认应用已成功启动。
     */
    @GetMapping("/hello")
    public Map<String, Object> hello() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("project", projectName);
        body.put("message", "萌宠诊所服务已启动，欢迎使用！");
        body.put("status", "RUNNING");
        body.put("time", LocalDateTime.now().toString());
        return body;
    }

}
