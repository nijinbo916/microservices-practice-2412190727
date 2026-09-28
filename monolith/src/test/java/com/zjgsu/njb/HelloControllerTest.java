package com.zjgsu.njb;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 接口验证测试：随机端口启动真实应用，实际发起 HTTP 请求访问
 * /api/hello 与 /actuator/health，确认接口可访问、健康检查返回 UP。
 *
 * <p>使用 JDK 原生 HttpClient，不依赖具体测试框架客户端 API，可重复运行。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HelloControllerTest {

    @LocalServerPort
    private int port;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void helloEndpointShouldReturnProjectInfo() throws Exception {
        HttpResponse<String> response = get("/api/hello");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("萌宠诊所");
        assertThat(response.body()).contains("RUNNING");
    }

    @Test
    void healthEndpointShouldReportUp() throws Exception {
        HttpResponse<String> response = get("/actuator/health");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"status\":\"UP\"");
    }

}
