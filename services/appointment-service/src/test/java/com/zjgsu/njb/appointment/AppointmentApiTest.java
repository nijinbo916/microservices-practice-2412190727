package com.zjgsu.njb.appointment;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;

/**
 * appointment-service API 层测试：201 成功 / 404 宠物不存在 / 503 依赖服务不可用。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AppointmentApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RestTemplate restTemplate;

    private MockRestServiceServer remote;

    @BeforeEach
    void setUp() {
        remote = MockRestServiceServer.bindTo(restTemplate).build();
    }

    private static final String BODY = """
            {"petId":1,"doctorId":1,
             "slotStart":"2026-11-05T10:00:00","slotEnd":"2026-11-05T10:30:00",
             "reason":"食欲不振，需要检查"}
            """;

    @Test
    void createAppointmentReturns201() throws Exception {
        remote.expect(requestTo("http://pet-service-stub/api/pets/1"))
                .andRespond(withSuccess(
                        "{\"id\":1,\"name\":\"豆豆\",\"species\":\"犬\",\"ownerName\":\"倪先生\"}",
                        MediaType.APPLICATION_JSON));
        remote.expect(requestTo("http://doctor-service-stub/api/doctors/1"))
                .andRespond(withSuccess(
                        "{\"id\":1,\"name\":\"李医生\",\"specialty\":\"内科\",\"dailyCapacity\":4}",
                        MediaType.APPLICATION_JSON));

        mockMvc.perform(post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.petName").value("豆豆"))
                .andExpect(jsonPath("$.doctorName").value("李医生"))
                .andExpect(jsonPath("$.status").value("BOOKED"));

        remote.verify();
    }

    @Test
    void missingPetReturns404() throws Exception {
        remote.expect(requestTo("http://pet-service-stub/api/pets/999"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        mockMvc.perform(post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY.replace("\"petId\":1", "\"petId\":999")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("宠物不存在")));

        remote.verify();
    }

    @Test
    void dependencyDownReturns503() throws Exception {
        remote.expect(requestTo("http://pet-service-stub/api/pets/1"))
                .andRespond(withServerError());

        mockMvc.perform(post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("DEPENDENCY_UNAVAILABLE"));

        remote.verify();
    }

    @Test
    void getMissingAppointmentReturns404() throws Exception {
        mockMvc.perform(get("/api/appointments/424242"))
                .andExpect(status().isNotFound());
    }
}
