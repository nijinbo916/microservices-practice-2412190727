package com.zjgsu.njb.pet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * 系统账号（第 09 周 · 认证服务）。
 *
 * <p>只存密码哈希，不存明文，也不存可逆密文。哈希算法是 BCrypt：
 * 自带随机盐、计算慢，即使数据库被拖走，也没法直接反推出密码，
 * 更不能像 MD5 那样靠彩虹表批量撞库。</p>
 *
 * <p>角色只有三种，打印在 token 里，网关按角色决定能不能放行：</p>
 * <ul>
 *   <li>{@code ADMIN} —— 管理员：看汇总、删档案</li>
 *   <li>{@code DOCTOR} —— 医生：看宠物/预约，不能建档</li>
 *   <li>{@code OWNER} —— 宠物主人：建档、下单预约</li>
 * </ul>
 */
@Entity
@Table(name = "sys_user")
public class UserAccount {

    /** 账号主键。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 登录名，全局唯一，不区分大小写由数据库排序规则决定。 */
    @Column(name = "username", nullable = false, unique = true, length = 50)
    private String username;

    /**
     * BCrypt 哈希，形如 {@code $2a$10$...}，固定 60 字符。
     * 列宽给 100，留出以后换算法（如 Argon2）的余量。
     */
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    /** 展示名，返回给前端显示，不参与任何判断。 */
    @Column(name = "display_name", nullable = false, length = 50)
    private String displayName;

    /** 角色，以字符串存库，避免枚举顺序变化导致历史数据错位。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Role role;

    /** 是否启用；停用的账号即使密码正确也拒绝登录。 */
    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    /** 建档时间，仅用于审计。 */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected UserAccount() {
        // JPA 需要无参构造
    }

    public UserAccount(String username, String passwordHash, String displayName, Role role) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.role = role;
        this.enabled = true;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Role getRole() {
        return role;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** 三种角色。 */
    public enum Role {
        ADMIN, DOCTOR, OWNER
    }
}
