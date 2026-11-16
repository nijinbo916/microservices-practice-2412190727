package com.zjgsu.njb.pet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zjgsu.njb.pet.repository.UserAccountRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 认证接口测试（第 09 周）。
 *
 * <p>覆盖作业要求的登录三件事：密码正确能拿到 token、密码错误拿不到、
 * 数据库里存的是 BCrypt 哈希而不是明文。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthApiTest {

    /** 与 application-test.yml 的 app.security.seed-password 一致。 */
    private static final String SEED_PASSWORD = "Test-Passw0rd-2026";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserAccountRepository userRepository;

    @Test
    @DisplayName("正常登录：返回 token、角色、有效期")
    void loginSucceeds() throws Exception {
        String body = """
                {"username":"admin","password":"%s"}""".formatted(SEED_PASSWORD);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.expiresIn").value(1800));
    }

    @Test
    @DisplayName("密码错误：401，且提示不区分「用户不存在」和「密码错」")
    void loginFailsWithWrongPassword() throws Exception {
        String body = """
                {"username":"admin","password":"definitely-wrong"}""";

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("用户名或密码错误"));
    }

    @Test
    @DisplayName("用户不存在：401，提示与密码错误完全一致（防用户名枚举）")
    void loginFailsWithUnknownUser() throws Exception {
        String body = """
                {"username":"nobody","password":"whatever"}""";

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("用户名或密码错误"));
    }

    @Test
    @DisplayName("密码存的是 BCrypt 哈希：以 $2a$/$2b$ 开头，且不等于明文")
    void passwordIsHashed() {
        var account = userRepository.findByUsername("owner").orElseThrow();

        assertThat(account.getPasswordHash())
                .startsWith("$2")
                .hasSize(60)
                .isNotEqualTo(SEED_PASSWORD);
    }

    @Test
    @DisplayName("/api/auth/me 没有网关身份头：401（证明直连拿不到身份）")
    void meWithoutGatewayHeadersIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("/api/auth/me 带网关身份头：返回身份与角色")
    void meWithGatewayHeaders() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                        .header("X-Auth-User", "doctor")
                        .header("X-Auth-Uid", "2")
                        .header("X-Auth-Roles", "DOCTOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("doctor"))
                .andExpect(jsonPath("$.roles[0]").value("DOCTOR"))
                .andExpect(jsonPath("$.authenticated").value(true));
    }
}
