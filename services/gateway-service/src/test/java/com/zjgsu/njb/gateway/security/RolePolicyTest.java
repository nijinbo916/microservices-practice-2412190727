package com.zjgsu.njb.gateway.security;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 鉴权规则单测：把「谁能调哪个接口」固定成测试，改规则时不会悄悄放开。
 *
 * <p>重点验证作业要求的两种拒绝：低权限角色碰管理接口被拦（403），
 * 以及删除接口只留给管理员。</p>
 */
class RolePolicyTest {

    private final RolePolicy policy = new RolePolicy();

    @Test
    @DisplayName("登录和健康检查是公开入口，不需要 token")
    void publicEndpoints() {
        assertTrue(policy.isPublic("/api/auth/login"));
        assertTrue(policy.isPublic("/api/health"));
        assertTrue(policy.isPublic("/actuator/health"));
    }

    @Test
    @DisplayName("普通接口：登录即可，不做角色限制")
    void authenticatedIsEnough() {
        assertNull(policy.check("GET", "/api/pets", List.of("OWNER")));
        assertNull(policy.check("GET", "/api/doctors/1", List.of("DOCTOR")));
    }

    @Test
    @DisplayName("管理接口：ADMIN 放行，OWNER / DOCTOR 被拒")
    void adminOnly() {
        assertNull(policy.check("GET", "/api/admin/summary", List.of("ADMIN")));
        assertNotNull(policy.check("GET", "/api/admin/summary", List.of("OWNER")));
        assertNotNull(policy.check("GET", "/api/admin/summary", List.of("DOCTOR")));
    }

    @Test
    @DisplayName("删除宠物：只有 ADMIN，医生也不行")
    void deleteRequiresAdmin() {
        assertNull(policy.check("DELETE", "/api/pets/3", List.of("ADMIN")));
        assertNotNull(policy.check("DELETE", "/api/pets/3", List.of("OWNER")));
        assertNotNull(policy.check("DELETE", "/api/pets/3", List.of("DOCTOR")));
    }

    @Test
    @DisplayName("建档：OWNER 可以，DOCTOR 不行")
    void createPetRequiresOwnerOrAdmin() {
        assertNull(policy.check("POST", "/api/pets", List.of("OWNER")));
        assertNull(policy.check("POST", "/api/pets", List.of("ADMIN")));
        assertNotNull(policy.check("POST", "/api/pets", List.of("DOCTOR")));
    }

    @Test
    @DisplayName("规则顺序：GET /api/pets 不会被 DELETE 规则误伤")
    void readIsNotBlockedByDeleteRule() {
        assertNull(policy.check("GET", "/api/pets/3", List.of("OWNER")));
    }
}
