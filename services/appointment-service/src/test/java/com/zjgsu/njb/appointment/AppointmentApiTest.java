package com.zjgsu.njb.appointment;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zjgsu.njb.appointment.remote.DoctorFeignClient;
import com.zjgsu.njb.appointment.remote.DoctorView;
import com.zjgsu.njb.appointment.remote.PetFeignClient;
import com.zjgsu.njb.appointment.remote.PetView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * appointment-service API 层测试（MockMvc + Mockito 桩 OpenFeign 客户端）。
 *
 * <p>第 08 周：跨服务调用已改为 OpenFeign，测试用 {@code @MockitoBean}
 * 替换 {@link PetFeignClient} / {@link DoctorFeignClient}，
 * 不连接真实 Nacos；404 / 500 场景用 {@code FeignStubs.error(status)}
 * 构造与真实调用一致的 {@code FeignException}。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class AppointmentApiTest {

    /** 替换真实 Feign 客户端：不连接 Nacos，不发真实请求。 */
    @MockitoBean
    private PetFeignClient petFeignClient;

    @MockitoBean
    private DoctorFeignClient doctorFeignClient;

    @Autowired
    private MockMvc mockMvc;

    // 预约时间必须晚于当前时间（DTO @Future 校验），动态生成未来时段，避免测试数据「过期」
    private static final LocalDateTime T1 =
            LocalDateTime.now().truncatedTo(ChronoUnit.HOURS).plusDays(3);
    private static final LocalDateTime T2 = T1.plusMinutes(30);

    private static String body(long petId) {
        return "{\"petId\":" + petId + ",\"doctorId\":1,"
                + "\"slotStart\":\"" + T1 + "\",\"slotEnd\":\"" + T2 + "\","
                + "\"reason\":\"食欲不振，需要检查\"}";
    }

    @BeforeEach
    void setUp() {
        when(petFeignClient.getPet(1L)).thenReturn(new PetView(1L, "豆豆", "犬", "倪先生"));
        when(doctorFeignClient.getDoctor(1L)).thenReturn(new DoctorView(1L, "李医生", "内科", 4));
    }

    @Test
    void createAppointmentReturns201() throws Exception {
        mockMvc.perform(post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(1)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.petName").value("豆豆"))
                .andExpect(jsonPath("$.doctorName").value("李医生"))
                .andExpect(jsonPath("$.status").value("BOOKED"));
    }

    @Test
    void missingPetReturns404() throws Exception {
        // 远端 pet-service 返回 404 → 接口返回 404 + 明确错误信息
        when(petFeignClient.getPet(999L)).thenThrow(FeignStubs.error(404, "getPet"));

        mockMvc.perform(post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(999)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("宠物不存在")));
    }

    @Test
    void dependencyDownReturns503() throws Exception {
        // 远端 pet-service 返回 500 → 接口返回 503（依赖不可用），不是 500 也不是假装成功
        when(petFeignClient.getPet(1L)).thenThrow(FeignStubs.error(500, "getPet"));

        mockMvc.perform(post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(1)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("DEPENDENCY_UNAVAILABLE"));
    }

    @Test
    void getMissingAppointmentReturns404() throws Exception {
        mockMvc.perform(get("/api/appointments/424242"))
                .andExpect(status().isNotFound());
    }
}
