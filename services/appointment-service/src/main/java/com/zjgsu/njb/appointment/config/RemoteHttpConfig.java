package com.zjgsu.njb.appointment.config;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * 跨服务 HTTP 客户端配置。
 *
 * <p>RestTemplate 是本服务访问 pet-service / doctor-service 的唯一通道，
 * 必须设置连接与读取超时：依赖服务卡死不能把本服务的线程也拖死。</p>
 *
 * <ul>
 *   <li>连接超时 {@code remote.connect-timeout-ms}，默认 3000ms</li>
 *   <li>读取超时 {@code remote.read-timeout-ms}，默认 5000ms</li>
 *   <li>服务地址 {@code pet.base-url} / {@code doctor.base-url}，
 *       可用环境变量 PET_SERVICE_URL / DOCTOR_SERVICE_URL 覆盖
 *       （compose 中配置为 http://pet:8080、http://doctor:8080）</li>
 * </ul>
 */
@Configuration
public class RemoteHttpConfig {

    @Bean
    public RestTemplate restTemplate(
            @Value("${remote.connect-timeout-ms:3000}") long connectTimeoutMs,
            @Value("${remote.read-timeout-ms:5000}") long readTimeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofMillis(connectTimeoutMs).toMillis());
        factory.setReadTimeout((int) Duration.ofMillis(readTimeoutMs).toMillis());
        return new RestTemplate(factory);
    }
}
