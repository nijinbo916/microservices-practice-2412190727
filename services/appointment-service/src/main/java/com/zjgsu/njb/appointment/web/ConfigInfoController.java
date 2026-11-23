package com.zjgsu.njb.appointment.web;

import com.zjgsu.njb.appointment.config.BookingProperties;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 配置观测接口（第 10 周作业的「可观察证据」）。
 *
 * <p>只返回<b>非敏感</b>配置项：环境名、配置分组、业务参数、连接池大小、
 * Feign 超时、日志级别。密钥、密码、JWT secret 一律不出现。</p>
 *
 * <pre>
 * GET /api/config/effective  -&gt; 200，返回当前进程实际生效的值
 * </pre>
 *
 * <p>用途：{@code scripts/config-test.sh} 在改 Nacos 配置前后各调一次，
 * 对比返回值即可证明「配置被服务读取」以及「改完立即生效、进程没重启」。</p>
 */
@RestController
@RequestMapping("/api/config")
public class ConfigInfoController {

    private final BookingProperties bookingProperties;
    private final Environment environment;

    public ConfigInfoController(BookingProperties bookingProperties, Environment environment) {
        this.bookingProperties = bookingProperties;
        this.environment = environment;
    }

    /** 当前生效配置快照（字段顺序固定，便于脚本和人阅读）。 */
    @GetMapping("/effective")
    public Map<String, Object> effective() {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("service", environment.getProperty("spring.application.name", "appointment-service"));
        view.put("activeProfiles", List.of(environment.getActiveProfiles()));
        view.put("env", environment.getProperty("petclinic.env", "(未设置)"));
        view.put("configGroup", environment.getProperty("spring.cloud.nacos.config.group", "(未设置)"));
        view.put("configServer", environment.getProperty("spring.cloud.nacos.config.server-addr", "(未设置)"));
        view.put("maxAdvanceDays", bookingProperties.getMaxAdvanceDays());
        view.put("hikariMaxPoolSize",
                environment.getProperty("spring.datasource.hikari.maximum-pool-size", "(未配置)"));
        view.put("feignReadTimeoutMs",
                environment.getProperty("spring.cloud.openfeign.client.config.default.read-timeout", "(未配置)"));
        view.put("appointmentLogLevel",
                environment.getProperty("logging.level.com.zjgsu.njb.appointment", "(未配置)"));
        return view;
    }
}
