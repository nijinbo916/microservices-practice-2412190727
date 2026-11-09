package com.zjgsu.njb.appointment.remote;

import com.zjgsu.njb.appointment.exception.DependencyUnavailableException;
import com.zjgsu.njb.appointment.exception.ResourceNotFoundException;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * pet-service 的调用门面（第 08 周：底层从 RestTemplate 换成 OpenFeign）。
 *
 * <p>业务代码（service 层）只依赖本门面，不直接接触 Feign 接口；
 * Feign 的异常在这里统一翻译成业务异常，失败语义与第 06/07 周保持一致：</p>
 *
 * <ul>
 *   <li>远端 404 → {@link ResourceNotFoundException}（404，中止写入）；</li>
 *   <li>远端其他非 2xx / 连接失败 / 超时 → {@link DependencyUnavailableException}（503，中止写入）；</li>
 *   <li>LoadBalancer 找不到任何实例（实例全部下线）→ {@link DependencyUnavailableException}（503，中止写入）。</li>
 * </ul>
 *
 * <p><b>关键原则不变：任何校验失败都不会被当成业务成功，本地预约表绝不写入。</b></p>
 */
@Component
public class PetClient {

    private static final Logger log = LoggerFactory.getLogger(PetClient.class);

    private final PetFeignClient petFeignClient;

    public PetClient(PetFeignClient petFeignClient) {
        this.petFeignClient = petFeignClient;
    }

    /** 校验宠物存在并返回视图（强依赖：任何失败都会阻止预约写入）。 */
    public PetView requirePet(Long id) {
        try {
            PetView pet = petFeignClient.getPet(id);
            if (pet == null) {
                throw new DependencyUnavailableException(
                        "pet-service 返回空响应：/api/pets/" + id);
            }
            return pet;
        } catch (FeignException.NotFound e) {
            log.info("pet-service 校验 404：宠物不存在 id={}", id);
            throw new ResourceNotFoundException(
                    "宠物不存在（pet-service 校验返回 404）：id=" + id);
        } catch (FeignException e) {
            log.warn("pet-service 调用失败：HTTP {}（{}）", e.status(), e.getMessage());
            throw new DependencyUnavailableException(
                    "pet-service 不可用（HTTP " + e.status() + "），已阻止本次预约写入");
        } catch (RuntimeException e) {
            // Spring Cloud LoadBalancer 在实例列表为空时抛 IllegalStateException
            //（不同小版本可能包成 FeignException 503，上一分支已覆盖；此处兜底）
            String msg = String.valueOf(e.getMessage()).toLowerCase();
            if (msg.contains("instance") || msg.contains("load balancer") || msg.contains("loadbalancer")) {
                log.warn("pet-service 没有可用实例：{}", e.getMessage());
                throw new DependencyUnavailableException(
                        "pet-service 当前没有可用实例（负载均衡器返回空实例列表），已阻止本次预约写入");
            }
            throw e;
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
}
