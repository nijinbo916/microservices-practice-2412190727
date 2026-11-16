package com.zjgsu.njb.pet.config;

import com.zjgsu.njb.pet.domain.UserAccount;
import com.zjgsu.njb.pet.repository.UserAccountRepository;
import com.zjgsu.njb.pet.security.AuthProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 测试账号初始化。
 *
 * <p>只在账号不存在时插入，重复启动不会产生重复数据，也不会覆盖已经改过的密码。
 * 密码统一来自环境变量 {@code APP_SEED_PASSWORD}，源码里没有写死的可用口令 ——
 * 仓库里只保留一个明显是占位符的默认值，README 里也标注了必须替换。</p>
 *
 * <p><b>为什么种子数据要放在应用里：</b>作业要求「密码以安全的哈希存储」，
 * 如果用 SQL 脚本插入，就得在脚本里写死哈希值，那个哈希值是固定的、可被复用；
 * 放在应用里用 BCrypt 现场生成，每次部署盐都不同，更接近真实做法。</p>
 */
@Component
public class SeedAccounts implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedAccounts.class);

    private final UserAccountRepository repository;
    private final PasswordEncoder encoder;
    private final AuthProperties properties;

    public SeedAccounts(UserAccountRepository repository,
                        PasswordEncoder encoder,
                        AuthProperties properties) {
        this.repository = repository;
        this.encoder = encoder;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        // 账号 / 展示名 / 角色；密码三个账号共用一个演示口令，便于测试脚本调用
        seed("admin", "系统管理员", UserAccount.Role.ADMIN);
        seed("doctor", "王医生", UserAccount.Role.DOCTOR);
        seed("owner", "李女士", UserAccount.Role.OWNER);
    }

    private void seed(String username, String displayName, UserAccount.Role role) {
        if (repository.existsByUsername(username)) {
            return;
        }
        String hash = encoder.encode(properties.seedPassword());
        repository.save(new UserAccount(username, hash, displayName, role));
        log.info("已创建测试账号：{}（角色 {}，密码为环境变量 APP_SEED_PASSWORD，未写入日志）",
                username, role);
    }
}
