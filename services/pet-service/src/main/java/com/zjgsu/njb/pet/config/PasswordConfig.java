package com.zjgsu.njb.pet.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 密码编码器配置。
 *
 * <p>强度用默认的 10（2^10 次迭代）。调高更安全但登录更慢，
 * 10 在本机约 60~80ms，既能拖住暴力破解，又不至于让登录明显卡顿。</p>
 *
 * <p>同一个 Bean 既用于登录校验（{@code matches}），也用于初始化种子账号
 * （{@code encode}）—— 保证「写进库的哈希」和「校验用的算法」永远一致。</p>
 */
@Configuration
public class PasswordConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }
}
