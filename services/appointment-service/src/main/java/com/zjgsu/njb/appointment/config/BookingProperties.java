package com.zjgsu.njb.appointment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

/**
 * 预约业务的可动态调整参数（第 10 周：由 Nacos Config 下发）。
 *
 * <p>Data ID：{@code appointment-service.yaml}，Group：{@code PETCLINIC_DEV} /
 * {@code PETCLINIC_TEST}。配置内容示例：</p>
 *
 * <pre>
 * petclinic:
 *   booking:
 *     max-advance-days: 7
 * </pre>
 *
 * <p>{@code @RefreshScope} + {@code refreshEnabled=true}：在 Nacos 控制台或
 * OpenAPI 改完值后，下一次请求就会用新值，服务不用重启、更不用重新打包。
 * 这是本次作业「动态配置验证」用的观察点。</p>
 */
@Component
@RefreshScope
@ConfigurationProperties(prefix = "petclinic.booking")
public class BookingProperties {

    /** 预约最多可提前多少天；超出则拒绝创建（400）。 */
    private int maxAdvanceDays = 30;

    public int getMaxAdvanceDays() {
        return maxAdvanceDays;
    }

    public void setMaxAdvanceDays(int maxAdvanceDays) {
        this.maxAdvanceDays = maxAdvanceDays;
    }
}
