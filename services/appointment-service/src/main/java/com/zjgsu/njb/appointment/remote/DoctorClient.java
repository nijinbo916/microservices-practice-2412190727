package com.zjgsu.njb.appointment.remote;

import com.zjgsu.njb.appointment.exception.DependencyUnavailableException;
import com.zjgsu.njb.appointment.exception.ResourceNotFoundException;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * doctor-service 的调用门面（第 08 周：底层从 RestTemplate 换成 OpenFeign）。
 *
 * <p>除校验医生存在外，还取回 {@code dailyCapacity} 供「每日号源上限」规则使用。
 * 失败语义与 {@link PetClient} 一致：强依赖失败一律阻止预约写入，
 * 绝不把调用失败当成业务成功。</p>
 */
@Component
public class DoctorClient {

    private static final Logger log = LoggerFactory.getLogger(DoctorClient.class);

    private final DoctorFeignClient doctorFeignClient;

    public DoctorClient(DoctorFeignClient doctorFeignClient) {
        this.doctorFeignClient = doctorFeignClient;
    }

    /** 校验医生存在并返回视图（强依赖）。 */
    public DoctorView requireDoctor(Long id) {
        try {
            DoctorView doctor = doctorFeignClient.getDoctor(id);
            if (doctor == null) {
                throw new DependencyUnavailableException(
                        "doctor-service 返回空响应：/api/doctors/" + id);
            }
            return doctor;
        } catch (FeignException.NotFound e) {
            log.info("doctor-service 校验 404：医生不存在 id={}", id);
            throw new ResourceNotFoundException(
                    "医生不存在（doctor-service 校验返回 404）：id=" + id);
        } catch (FeignException e) {
            log.warn("doctor-service 调用失败：HTTP {}（{}）", e.status(), e.getMessage());
            throw new DependencyUnavailableException(
                    "doctor-service 不可用（HTTP " + e.status() + "），已阻止本次预约写入");
        } catch (RuntimeException e) {
            String msg = String.valueOf(e.getMessage()).toLowerCase();
            if (msg.contains("instance") || msg.contains("load balancer") || msg.contains("loadbalancer")) {
                log.warn("doctor-service 没有可用实例：{}", e.getMessage());
                throw new DependencyUnavailableException(
                        "doctor-service 当前没有可用实例（负载均衡器返回空实例列表），已阻止本次预约写入");
            }
            throw e;
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
}
