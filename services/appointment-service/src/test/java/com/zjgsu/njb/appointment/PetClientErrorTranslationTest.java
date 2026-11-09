package com.zjgsu.njb.appointment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.zjgsu.njb.appointment.exception.DependencyUnavailableException;
import com.zjgsu.njb.appointment.exception.ResourceNotFoundException;
import com.zjgsu.njb.appointment.remote.DoctorFeignClient;
import com.zjgsu.njb.appointment.remote.DoctorView;
import com.zjgsu.njb.appointment.remote.PetClient;
import com.zjgsu.njb.appointment.remote.PetFeignClient;
import com.zjgsu.njb.appointment.remote.PetView;
import feign.FeignException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Feign 客户端错误处理测试（纯 Mockito 单测，不起 Spring 容器）。
 *
 * <p>验证 {@link PetClient} 门面把 Feign 的各种失败翻译成正确的业务异常：</p>
 * <ul>
 *   <li>远端 404 → {@link ResourceNotFoundException}（业务语义：资源不存在）；</li>
 *   <li>远端 500 / 504（等价超时）→ {@link DependencyUnavailableException}（503）；</li>
 *   <li>负载均衡器实例列表为空 → {@link DependencyUnavailableException}（503）；</li>
 *   <li>弱依赖（findPet）任何失败都降级返回 null，不抛异常。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OpenFeign 客户端错误处理")
class PetClientErrorTranslationTest {

    @Mock
    private PetFeignClient petFeignClient;

    @Mock
    private DoctorFeignClient doctorFeignClient;

    @InjectMocks
    private PetClient petClient;

    @InjectMocks
    private com.zjgsu.njb.appointment.remote.DoctorClient doctorClient;

    @Test
    @DisplayName("远端 404 → ResourceNotFoundException（404，不是 503）")
    void notFoundTranslatesToResourceNotFound() {
        when(petFeignClient.getPet(anyLong())).thenThrow(FeignStubs.error(404, "getPet"));

        assertThatThrownBy(() -> petClient.requirePet(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("宠物不存在");
    }

    @Test
    @DisplayName("远端 500 → DependencyUnavailableException（503，不是吞掉错误）")
    void serverErrorTranslatesToDependencyUnavailable() {
        when(petFeignClient.getPet(anyLong())).thenThrow(FeignStubs.error(500, "getPet"));

        assertThatThrownBy(() -> petClient.requirePet(1L))
                .isInstanceOf(DependencyUnavailableException.class)
                .hasMessageContaining("pet-service");
    }

    @Test
    @DisplayName("远端 504（读取超时）→ DependencyUnavailableException（503）")
    void gatewayTimeoutTranslatesToDependencyUnavailable() {
        when(petFeignClient.getPet(anyLong())).thenThrow(FeignStubs.error(504, "getPet"));

        assertThatThrownBy(() -> petClient.requirePet(1L))
                .isInstanceOf(DependencyUnavailableException.class)
                .hasMessageContaining("pet-service");
    }

    @Test
    @DisplayName("负载均衡器实例列表为空 → DependencyUnavailableException（503）")
    void noAvailableInstanceTranslatesToDependencyUnavailable() {
        when(petFeignClient.getPet(anyLong()))
                .thenThrow(new IllegalStateException("No instances available for pet-service"));

        assertThatThrownBy(() -> petClient.requirePet(1L))
                .isInstanceOf(DependencyUnavailableException.class)
                .hasMessageContaining("没有可用实例");
    }

    @Test
    @DisplayName("成功路径：返回远端视图")
    void successReturnsView() {
        when(petFeignClient.getPet(anyLong()))
                .thenReturn(new PetView(1L, "豆豆", "犬", "倪先生"));

        PetView pet = petClient.requirePet(1L);
        assertThat(pet.name()).isEqualTo("豆豆");
    }

    @Test
    @DisplayName("弱依赖：findPet 任何失败都降级返回 null")
    void weakDependencyDegradesToNull() {
        when(petFeignClient.getPet(anyLong())).thenThrow(FeignStubs.error(503, "getPet"));

        assertThat(petClient.findPet(1L)).isNull();
    }

    @Test
    @DisplayName("doctor 门面同样处理 404 / 500 / 空响应")
    void doctorFacadeHandlesErrors() {
        // 抛异常的桩要用 doThrow 打桩：when() 写法会在重新打桩时先执行 mock 而抛出上一次的异常
        org.mockito.Mockito.doThrow(FeignStubs.error(404, "getDoctor"))
                .when(doctorFeignClient).getDoctor(anyLong());
        assertThatThrownBy(() -> doctorClient.requireDoctor(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("医生不存在");

        org.mockito.Mockito.doThrow(FeignStubs.error(500, "getDoctor"))
                .when(doctorFeignClient).getDoctor(anyLong());
        assertThatThrownBy(() -> doctorClient.requireDoctor(1L))
                .isInstanceOf(DependencyUnavailableException.class)
                .hasMessageContaining("doctor-service");

        org.mockito.Mockito.doReturn(null).when(doctorFeignClient).getDoctor(anyLong());
        assertThat(doctorClient.findDoctor(1L)).isNull();
    }
}
