package com.zjgsu.njb.pet.web;

import com.zjgsu.njb.pet.domain.UserAccount;
import com.zjgsu.njb.pet.repository.PetRepository;
import com.zjgsu.njb.pet.repository.UserAccountRepository;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 管理端接口：只有 ADMIN 能访问。
 *
 * <p>这是作业里「至少 1 个接口需要角色校验」的落点：
 * 网关按 {@code GET /api/admin/** -> ADMIN} 的规则拦截，
 * OWNER / DOCTOR 的 token 在这里会拿到 403（由网关返回）。</p>
 *
 * <p><b>为什么服务里还要再查一次角色：</b>网关是第一道闸，但不是唯一一道。
 * 假设有人从内网直连 8081（或网关配置被改错），只靠网关就等于把全部安全
 * 押在一个组件上。这里再校验一次 {@code X-Auth-Roles}，属于纵深防御：
 * 网关漏了，这一层还能挡住。</p>
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final PetRepository petRepository;
    private final UserAccountRepository userRepository;

    public AdminController(PetRepository petRepository, UserAccountRepository userRepository) {
        this.petRepository = petRepository;
        this.userRepository = userRepository;
    }

    /** 全站汇总，仅管理员可见。 */
    @GetMapping("/summary")
    public Map<String, Object> summary(
            @RequestHeader(value = "X-Auth-Roles", required = false) String roles,
            @RequestHeader(value = "X-Auth-User", required = false) String username) {

        List<String> roleList = roles == null || roles.isBlank()
                ? List.of()
                : Arrays.stream(roles.split(",")).map(String::trim).toList();
        if (!roleList.contains("ADMIN")) {
            // 正常情况下网关已经返回 403，这里是兜底
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "需要 ADMIN 角色");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("petCount", petRepository.count());
        result.put("userCount", userRepository.count());
        result.put("roles", Map.of(
                "ADMIN", userRepository.countByRole(UserAccount.Role.ADMIN),
                "DOCTOR", userRepository.countByRole(UserAccount.Role.DOCTOR),
                "OWNER", userRepository.countByRole(UserAccount.Role.OWNER)
        ));
        result.put("requestedBy", username);
        return result;
    }
}
