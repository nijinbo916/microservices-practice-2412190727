package com.zjgsu.njb.pet.repository;

import com.zjgsu.njb.pet.domain.UserAccount;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 系统账号数据访问接口（只操作本服务的 sys_user 表）。
 *
 * <p>登录时不查询「用户名 + 密码」，因为密码是 BCrypt 哈希、每次盐都不同，
 * 无法用 SQL 比对。只能按用户名取出哈希，再交给 BCrypt 校验 ——
 * 这是使用强哈希的必然做法，也顺带避免了 SQL 层面的时序差异。</p>
 */
public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    /** 按登录名查账号。 */
    Optional<UserAccount> findByUsername(String username);

    /** 判断登录名是否已存在（种子数据初始化用）。 */
    boolean existsByUsername(String username);

    /** 按角色统计账号数（管理端汇总用）。 */
    long countByRole(UserAccount.Role role);
}
