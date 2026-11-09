package com.zjgsu.njb.appointment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zjgsu.njb.appointment.dto.CreateAppointmentRequest;
import com.zjgsu.njb.appointment.exception.DependencyUnavailableException;
import com.zjgsu.njb.appointment.remote.DoctorFeignClient;
import com.zjgsu.njb.appointment.remote.DoctorView;
import com.zjgsu.njb.appointment.remote.PetFeignClient;
import com.zjgsu.njb.appointment.repository.AppointmentRepository;
import com.zjgsu.njb.appointment.service.AppointmentService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 「负载均衡器实例列表为空」的测试：Mockito 桩 Feign 客户端抛出
 * Spring Cloud LoadBalancer 无实例时的典型异常，不连接真实 Nacos。
 *
 * <p>验证三件事：</p>
 * <ol>
 *   <li>实例列表为空时，业务流程抛 {@link DependencyUnavailableException}；</li>
 *   <li>接口层返回 503（依赖不可用），不是跳过校验继续写入；</li>
 *   <li>两种情况本地预约表都没有新增记录。</li>
 * </ol>
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class EmptyInstanceDiscoveryTest {

    private static final LocalDateTime T1 = LocalDateTime.of(2026, 11, 20, 9, 0);
    private static final LocalDateTime T2 = LocalDateTime.of(2026, 11, 20, 9, 30);

    @MockitoBean
    private PetFeignClient petFeignClient;

    @MockitoBean
    private DoctorFeignClient doctorFeignClient;

    @Autowired
    private AppointmentService appointmentService;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        appointmentRepository.deleteAll();
        // doctor 正常；pet 的实例列表为空（LoadBalancer 抛 IllegalStateException，门面转 503）
        when(doctorFeignClient.getDoctor(anyLong())).thenReturn(
                new DoctorView(1L, "李医生", "内科", 4));
        when(petFeignClient.getPet(anyLong())).thenThrow(
                new IllegalStateException("No instances available for pet-service"));
    }

    @Test
    void emptyInstanceListBlocksBusinessFlow() {
        assertThatThrownBy(() -> appointmentService.createAppointment(
                new CreateAppointmentRequest(1L, 1L, T1, T2, "无可用实例")))
                .isInstanceOf(DependencyUnavailableException.class)
                .hasMessageContaining("没有可用实例");

        assertThat(appointmentRepository.count()).isZero();
    }

    @Test
    void emptyInstanceListReturns503AndWritesNothing() throws Exception {
        mockMvc.perform(post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"petId":1,"doctorId":1,
                                 "slotStart":"2026-11-20T09:00:00","slotEnd":"2026-11-20T09:30:00",
                                 "reason":"无可用实例"}
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.error").value("DEPENDENCY_UNAVAILABLE"));

        assertThat(appointmentRepository.count()).isZero();
    }

}
