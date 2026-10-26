package com.zjgsu.njb.doctor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zjgsu.njb.doctor.repository.DoctorRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * doctor-service 测试：上下文装配、种子数据、API 行为（H2 内存库，可重复）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DoctorApiTest {

    @Autowired
    private DoctorRepository doctorRepository;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void contextLoads() {
        assertThat(doctorRepository).isNotNull();
    }

    @Test
    void seedDataLoaded() {
        // data.sql 灌入的 3 名医生就绪
        assertThat(doctorRepository.count()).isEqualTo(3);
    }

    @Test
    void listDoctors() throws Exception {
        mockMvc.perform(get("/api/doctors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].name").value("李医生"))
                .andExpect(jsonPath("$[0].dailyCapacity").value(4));
    }

    @Test
    void getDoctorDetail() throws Exception {
        mockMvc.perform(get("/api/doctors/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.specialty").value("内科"));
    }

    @Test
    void getMissingDoctorReturns404() throws Exception {
        mockMvc.perform(get("/api/doctors/99999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }
}
