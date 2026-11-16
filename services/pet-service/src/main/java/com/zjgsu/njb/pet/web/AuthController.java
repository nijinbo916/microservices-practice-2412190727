package com.zjgsu.njb.pet.web;

import com.zjgsu.njb.pet.dto.LoginRequest;
import com.zjgsu.njb.pet.dto.LoginResponse;
import com.zjgsu.njb.pet.dto.MeResponse;
import com.zjgsu.njb.pet.service.AuthService;
import jakarta.validation.Valid;
import java.util.Arrays;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 认证接口。
 *
 * <pre>
 * POST /api/auth/login  提交用户名密码  -&gt; 200 { accessToken, role, ... } / 401
 * GET  /api/auth/me     查看当前身份    -&gt; 200（读网关注入的头）/ 401
 * </pre>
 *
 * <p><b>登录接口为什么在业务服务而不是网关：</b>账号数据和校验逻辑属于用户域，
 * 网关只做无状态的验签。这样网关不持有用户表，也不需要在网关里写数据库代码，
 * 职责边界清晰；代价是登录请求也走网关转发（第 4 条路由 /api/auth/**）。</p>
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** 登录，成功返回 JWT。失败抛 InvalidCredentialsException，由全局处理器转 401。 */
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        AuthService.LoginResult result = authService.login(request.username(), request.password());
        return LoginResponse.of(result.account(), result.token());
    }

    /**
     * 当前身份。
     *
     * <p>不重新解析 token，只读 {@code X-Auth-User} / {@code X-Auth-Roles} ——
     * 这两个头由网关在校验通过后写入，且客户端自带的同名头已在网关被删除。
     * 拿不到头说明请求没经过网关（直连端口），返回 401。</p>
     */
    @GetMapping("/me")
    public MeResponse me(@RequestHeader(value = "X-Auth-User", required = false) String username,
                         @RequestHeader(value = "X-Auth-Uid", required = false) String userId,
                         @RequestHeader(value = "X-Auth-Roles", required = false) String roles) {
        if (username == null || username.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "缺少网关身份头：该接口只接受经过 API Gateway 的请求");
        }
        List<String> roleList = roles == null || roles.isBlank()
                ? List.of()
                : Arrays.stream(roles.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        Long uid = null;
        if (userId != null && !userId.isBlank()) {
            try {
                uid = Long.valueOf(userId.trim());
            } catch (NumberFormatException ignored) {
                uid = null;
            }
        }
        return new MeResponse(username, uid, roleList, true);
    }
}
