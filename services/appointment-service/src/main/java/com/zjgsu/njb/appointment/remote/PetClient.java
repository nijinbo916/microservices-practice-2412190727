package com.zjgsu.njb.appointment.remote;

import com.zjgsu.njb.appointment.exception.DependencyUnavailableException;
import com.zjgsu.njb.appointment.exception.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

/**
 * pet-service 的 HTTP 客户端（RestTemplate）。
 *
 * <p><b>失败语义（本作业的关键要求）：绝不跳过校验继续写本地数据。</b></p>
 * <ul>
 *   <li>远端 404 → 抛 {@link ResourceNotFoundException}（映射 404，预约创建中止）；</li>
 *   <li>远端其他 4xx/5xx、连接失败、读取超时 → 抛
 *       {@link DependencyUnavailableException}（映射 503，预约创建中止）；</li>
 *   <li>查询展示用的 {@link #findPet(Long)} 不抛异常，失败返回 null（可降级）。</li>
 * </ul>
 */
@Component
public class PetClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public PetClient(RestTemplate restTemplate,
                     @Value("${pet.base-url:http://localhost:8081}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    /** 校验宠物存在并返回视图（强依赖：任何失败都会阻止预约写入）。 */
    public PetView requirePet(Long id) {
        String url = baseUrl + "/api/pets/" + id;
        try {
            ResponseEntity<PetView> resp = restTemplate.getForEntity(url, PetView.class);
            if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
                throw new DependencyUnavailableException(
                        "pet-service 返回异常状态 " + resp.getStatusCode() + "：/api/pets/" + id);
            }
            return resp.getBody();
        } catch (HttpStatusCodeException e) {
            if (e.getStatusCode().value() == 404) {
                throw new ResourceNotFoundException(
                        "宠物不存在（pet-service 校验返回 404）：id=" + id);
            }
            throw new DependencyUnavailableException(
                    "pet-service 返回错误 " + e.getStatusCode() + "，已阻止本次预约写入");
        } catch (ResourceAccessException e) {
            throw new DependencyUnavailableException(
                    "pet-service 不可达（" + rootCause(e) + "），已阻止本次预约写入");
        }
    }

    /** 查询宠物用于展示增强（弱依赖：失败返回 null，调用方降级显示）。 */
    public PetView findPet(Long id) {
        try {
            return requirePet(id);
        } catch (ResourceNotFoundException | DependencyUnavailableException e) {
            return null;
        }
    }

    private static String rootCause(ResourceAccessException e) {
        Throwable c = e.getCause() != null ? e.getCause() : e;
        return c.getClass().getSimpleName() + ": " + c.getMessage();
    }
}
