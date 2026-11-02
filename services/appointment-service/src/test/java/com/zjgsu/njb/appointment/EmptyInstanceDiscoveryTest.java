package com.zjgsu.njb.appointment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zjgsu.njb.appointment.dto.CreateAppointmentRequest;
import com.zjgsu.njb.appointment.exception.DependencyUnavailableException;
import com.zjgsu.njb.appointment.repository.AppointmentRepository;
import com.zjgsu.njb.appointment.service.AppointmentService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 「实例列表为空」的测试：模拟 DiscoveryClient，不连接真实 Nacos。
 *
 * <p>验证三件事：</p>
 * <ol>
 *   <li>服务发现拿不到实例时，业务流程抛 {@link DependencyUnavailableException}；</li>
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
    private DiscoveryClient discoveryClient;

    @Autowired
    private AppointmentService appointmentService;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        appointmentRepository.deleteAll();
        when(discoveryClient.getInstances(anyString())).thenReturn(List.of());
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
