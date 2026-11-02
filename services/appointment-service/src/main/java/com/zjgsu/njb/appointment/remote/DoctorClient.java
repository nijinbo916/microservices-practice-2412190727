package com.zjgsu.njb.appointment.remote;

import com.zjgsu.njb.appointment.exception.DependencyUnavailableException;
import com.zjgsu.njb.appointment.exception.ResourceNotFoundException;
import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

/**
 * doctor-service 的 HTTP 客户端（RestTemplate + Nacos 服务发现）。
 *
 * <p>地址同样由 {@link ServiceLookup} 按服务名实时解析；
 * 除校验医生存在外，还取回 {@code dailyCapacity} 供「每日号源上限」规则使用。
 * 失败语义与 {@link PetClient} 一致：强依赖失败一律阻止预约写入。</p>
 */
@Component
public class DoctorClient {

    private final RestTemplate restTemplate;
    private final ServiceLookup serviceLookup;
    private final String serviceName;

    public DoctorClient(RestTemplate restTemplate,
                        ServiceLookup serviceLookup,
                        @Value("${doctor.service-name:doctor-service}") String serviceName) {
        this.restTemplate = restTemplate;
        this.serviceLookup = serviceLookup;
        this.serviceName = serviceName;
    }

    /** 校验医生存在并返回视图（强依赖）。 */
    public DoctorView requireDoctor(Long id) {
        URI base = serviceLookup.resolve(serviceName);
        String url = base + "/api/doctors/" + id;
        try {
            ResponseEntity<DoctorView> resp = restTemplate.getForEntity(url, DoctorView.class);
            if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
                throw new DependencyUnavailableException(
                        "doctor-service 返回异常状态 " + resp.getStatusCode() + "：/api/doctors/" + id);
            }
            return resp.getBody();
        } catch (HttpStatusCodeException e) {
            if (e.getStatusCode().value() == 404) {
                throw new ResourceNotFoundException(
                        "医生不存在（doctor-service 校验返回 404）：id=" + id);
            }
            throw new DependencyUnavailableException(
                    "doctor-service 返回错误 " + e.getStatusCode() + "，已阻止本次预约写入");
        } catch (ResourceAccessException e) {
            throw new DependencyUnavailableException(
                    "doctor-service 不可达（" + rootCause(e) + "），已阻止本次预约写入");
        }
    }

    /** 查询医生用于展示增强（弱依赖：失败返回 null）。 */
    public DoctorView findDoctor(Long id) {
        try {
            return requireDoctor(id);
        } catch (ResourceNotFoundException | DependencyUnavailableException e) {
            return null;
        }
    }

    private static String rootCause(ResourceAccessException e) {
        Throwable c = e.getCause() != null ? e.getCause() : e;
        return c.getClass().getSimpleName() + ": " + c.getMessage();
    }
}
