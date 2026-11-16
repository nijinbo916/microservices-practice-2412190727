package com.zjgsu.njb.pet.service;

import com.zjgsu.njb.pet.domain.UserAccount;
import com.zjgsu.njb.pet.exception.InvalidCredentialsException;
import com.zjgsu.njb.pet.repository.UserAccountRepository;
import com.zjgsu.njb.pet.security.JwtIssuer;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 认证服务：校验账号密码，签发 JWT。
 *
 * <p>登录流程只有三步，但每步都有意为之：</p>
 * <ol>
 *   <li><b>按用户名取账号</b>：不做「用户名+密码」的 SQL 查询，
 *       因为 BCrypt 哈希每次盐不同，SQL 里比不了。</li>
 *   <li><b>用 BCrypt 校验</b>：哈希里带着盐和强度参数，{@code matches} 会自己解析。</li>
 *   <li><b>签发 token</b>：只放用户名、id、角色，不放业务数据。</li>
 * </ol>
 *
 * <p><b>防用户名枚举：</b>用户不存在时也跑一次 BCrypt 比对（对着一个固定的假哈希），
 * 让「用户不存在」和「密码错误」耗时接近。否则攻击者靠响应时间差就能判断
 * 哪些用户名是真实存在的。</p>
 */
@Service
public class AuthService {

    private static final String UNIFIED_FAILURE = "用户名或密码错误";

    private final UserAccountRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtIssuer jwtIssuer;

    /** 用户不存在时拿出来比对的假哈希，启动时生成一次；内容不重要，只要格式合法。 */
    private final String decoyHash;

    public AuthService(UserAccountRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtIssuer jwtIssuer) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtIssuer = jwtIssuer;
        this.decoyHash = passwordEncoder.encode("decoy-password-for-timing-only");
    }

    /**
     * 登录。
     *
     * @throws InvalidCredentialsException 账号不存在 / 密码错 / 账号停用（对外统一同一句话）
     */
    @Transactional(readOnly = true)
    public LoginResult login(String username, String rawPassword) {
        String normalized = username == null ? "" : username.trim();

        UserAccount account = userRepository.findByUsername(normalized).orElse(null);
        if (account == null) {
            // 故意走一次哈希比对，抹平时间差
            passwordEncoder.matches(rawPassword == null ? "" : rawPassword, decoyHash);
            throw new InvalidCredentialsException(UNIFIED_FAILURE);
        }
        if (!passwordEncoder.matches(rawPassword == null ? "" : rawPassword, account.getPasswordHash())) {
            throw new InvalidCredentialsException(UNIFIED_FAILURE);
        }
        if (!account.isEnabled()) {
            throw new InvalidCredentialsException(UNIFIED_FAILURE);
        }

        JwtIssuer.IssuedToken issued = jwtIssuer.issue(account);
        return new LoginResult(account, issued);
    }

    /** 登录结果：账号信息 + 令牌，控制器拿去拼响应。 */
    public record LoginResult(UserAccount account, JwtIssuer.IssuedToken token) {
    }
}
