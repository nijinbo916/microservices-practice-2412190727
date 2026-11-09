package com.zjgsu.njb.appointment;

import feign.FeignException;
import feign.Request;
import feign.Response;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;

/**
 * 测试专用：构造指定 HTTP 状态码的 {@link FeignException}。
 *
 * <p>单元测试不连接真实 pet-service / doctor-service，
 * {@code FeignException.errorStatus} 会按状态码返回 Feign 的真实异常子类
 * （404 → {@link FeignException.NotFound}，5xx → 对应子类），
 * 与容器内真实调用时抛出的异常类型一致。</p>
 */
public final class FeignStubs {

    private FeignStubs() {
    }

    /** 构造一个 status 对应的 FeignException（与真实 HTTP 调用失败时抛出的类型相同）。 */
    public static FeignException error(int status, String methodKey) {
        Response response = Response.builder()
                .status(status)
                .reason("stub-" + status)
                .headers(Collections.emptyMap())
                .request(Request.create(Request.HttpMethod.GET, "http://stub" + methodKey,
                        Map.of(), null, StandardCharsets.UTF_8, null))
                .build();
        return FeignException.errorStatus(methodKey, response);
    }
}
