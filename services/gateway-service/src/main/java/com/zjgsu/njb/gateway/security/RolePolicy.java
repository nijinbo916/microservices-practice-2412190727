package com.zjgsu.njb.gateway.security;

import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

/**
 * 鉴权规则表：哪个接口需要哪个角色。
 *
 * <p>规则是「白名单 + 默认拒绝」模型：</p>
 * <ul>
 *   <li>不在 {@link #PUBLIC_ENDPOINTS} 里的 {@code /api/**}，默认必须登录（有合法 token）</li>
 *   <li>命中下面 RULES 的，在上述基础上再要求角色之一</li>
 * </ul>
 *
 * <p>规则按顺序匹配，<b>先匹配到的先生效</b>，所以更具体的规则要写在前面。
 * 例如 {@code DELETE /api/pets/**} 必须排在 {@code GET /api/pets/**} 之前，
 * 否则删除接口会被「登录即可」的宽松规则提前放行。</p>
 */
@Component
public class RolePolicy {

    private final AntPathMatcher matcher = new AntPathMatcher();

    /** 不需要 token 的入口：登录、健康检查。 */
    private static final List<String> PUBLIC_ENDPOINTS = List.of(
            "/api/auth/login",
            "/api/health",
            "/actuator/health",
            "/actuator/health/**"
    );

    /**
     * 角色规则。{@code roles} 为空表示「登录即可」，不做角色限制。
     *
     * <p>三种角色来自认证服务：ADMIN（管理员）/ DOCTOR（医生）/ OWNER（宠物主人）。</p>
     */
    private static final List<Rule> RULES = List.of(
            // 管理端：只有管理员能看全站汇总
            new Rule("GET", "/api/admin/**", Set.of("ADMIN")),
            // 删除宠物档案：只有管理员能删（越权演示用的就是这个）
            new Rule("DELETE", "/api/pets/**", Set.of("ADMIN")),
            // 建档：宠物主人或管理员，医生不能建
            new Rule("POST", "/api/pets", Set.of("OWNER", "ADMIN")),
            // 建预约：主人下单、医生/管理员也可以代约
            new Rule("POST", "/api/appointments", Set.of("OWNER", "DOCTOR", "ADMIN")),
            // 其余 /api/pets、/api/doctors、/api/appointments 的读接口：登录即可
            new Rule("*", "/api/**", Set.of())
    );

    /** 是否公开入口（免 token）。 */
    public boolean isPublic(String path) {
        return PUBLIC_ENDPOINTS.stream().anyMatch(p -> matcher.match(p, path));
    }

    /**
     * 判断该请求是否被允许。
     *
     * @return 允许返回 null；被拒返回说明文字（用于 403 响应体）
     */
    public String check(String method, String path, List<String> roles) {
        for (Rule rule : RULES) {
            boolean methodMatch = "*".equals(rule.method()) || rule.method().equalsIgnoreCase(method);
            if (!methodMatch || !matcher.match(rule.path(), path)) {
                continue;
            }
            if (rule.roles().isEmpty()) {
                return null;
            }
            boolean allowed = rule.roles().stream().anyMatch(roles::contains);
            return allowed ? null : "需要角色 " + rule.roles() + "，当前角色 " + roles;
        }
        return null;
    }

    private record Rule(String method, String path, Set<String> roles) {
    }
}
