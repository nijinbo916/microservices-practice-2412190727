package com.zjgsu.njb.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 网关安全配置，全部来自环境变量，代码里不出现真实密钥。
 *
 * @param jwtSecret     HS256 签名密钥，至少 32 字节；必须与 pet-service 签发端一致
 * @param issuer        JWT 签发者，验签时强校验，防止别的系统签的 token 混进来
 * @param trustedCaller 网关向下游证明「这个请求确实经过网关」的共享密钥，
 *                      下游（pet-service）会比对；客户端自带的同名头会被网关删掉
 */
@ConfigurationProperties(prefix = "app.security")
public record GatewaySecurityProperties(
        String jwtSecret,
        String issuer,
        String trustedCaller
) {

    public GatewaySecurityProperties {
        if (jwtSecret == null || jwtSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "app.security.jwt-secret 未配置或长度不足 32 字节，拒绝启动（HS256 要求密钥 >= 256 位）");
        }
        if (issuer == null || issuer.isBlank()) {
            issuer = "petclinic-auth";
        }
        if (trustedCaller == null || trustedCaller.isBlank()) {
            throw new IllegalStateException("app.security.trusted-caller 未配置，下游无法识别网关请求");
        }
    }
}
