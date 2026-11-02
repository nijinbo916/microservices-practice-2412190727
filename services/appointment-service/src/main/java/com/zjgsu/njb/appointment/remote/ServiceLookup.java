package com.zjgsu.njb.appointment.remote;

import com.zjgsu.njb.appointment.exception.DependencyUnavailableException;
import java.net.URI;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.stereotype.Component;

/**
 * 服务实例寻址器：把「服务名」解析成「当前可用的实例地址」。
 *
 * <p><b>关键点：每次调用都重新向 {@link DiscoveryClient} 查询实例列表，</b>
 * 不在启动时解析一次就缓存永久地址 —— 这样实例下线、重启换 IP 后能立即感知，
 * 也能在实例列表为空时立刻失败，而不是把请求打到一个已经消失的地址上。</p>
 *
 * <p>实例列表为空（服务未注册 / 已全部下线）时抛
 * {@link DependencyUnavailableException}，由全局异常处理映射为 503，
 * 调用方据此中止业务流程，绝不跳过校验写入本地数据。</p>
 */
@Component
public class ServiceLookup {

    private static final Logger log = LoggerFactory.getLogger(ServiceLookup.class);

    private final DiscoveryClient discoveryClient;

    public ServiceLookup(DiscoveryClient discoveryClient) {
        this.discoveryClient = discoveryClient;
    }

    /**
     * 取指定服务的一个可用实例地址。
     *
     * @param serviceName Nacos 中注册的服务名（与 spring.application.name 一致）
     * @return 实例 URI，例如 http://172.18.0.5:8080
     * @throws DependencyUnavailableException 当前没有任何可用实例
     */
    public URI resolve(String serviceName) {
        List<ServiceInstance> instances = discoveryClient.getInstances(serviceName);
        if (instances == null || instances.isEmpty()) {
            log.warn("服务 {} 当前没有可用实例（Nacos 中的实例列表为空）", serviceName);
            throw new DependencyUnavailableException(
                    "服务 " + serviceName + " 当前没有可用实例（服务发现返回空列表），已阻止本次预约写入");
        }
        ServiceInstance instance = instances.get(0);
        log.info("服务发现：{} -> {}（共 {} 个实例）",
                serviceName, instance.getUri(), instances.size());
        return instance.getUri();
    }
}
