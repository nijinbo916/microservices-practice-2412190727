package com.zjgsu.njb.appointment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;

import com.zjgsu.njb.appointment.domain.Appointment;
import com.zjgsu.njb.appointment.dto.AppointmentResponse;
import com.zjgsu.njb.appointment.dto.CreateAppointmentRequest;
import com.zjgsu.njb.appointment.exception.BusinessRuleException;
import com.zjgsu.njb.appointment.exception.DependencyUnavailableException;
import com.zjgsu.njb.appointment.exception.ResourceNotFoundException;
import com.zjgsu.njb.appointment.repository.AppointmentRepository;
import com.zjgsu.njb.appointment.service.AppointmentService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

/**
 * appointment-service 业务规则 + 跨服务调用测试（H2 + MockRestServiceServer）。
 *
 * <p>覆盖四条业务规则中的三条（时段合法在 DTO 校验层）以及
 * 「依赖资源不存在 / 依赖服务不可用时绝不写入本地数据」的关键语义。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class AppointmentServiceTest {

    @Autowired
    private AppointmentService appointmentService;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private RestTemplate restTemplate;

    private MockRestServiceServer remote;

    private static final LocalDateTime T1 =
            LocalDateTime.of(2026, 11, 5, 10, 0);
    private static final LocalDateTime T2 =
            LocalDateTime.of(2026, 11, 5, 10, 30);

    @BeforeEach
    void setUp() {
        remote = MockRestServiceServer.bindTo(restTemplate).build();
        appointmentRepository.deleteAll();
    }

    private void stubPetOk(long petId) {
        remote.expect(requestTo("http://pet-service-stub/api/pets/" + petId))
                .andRespond(withSuccess(
                        "{\"id\":" + petId + ",\"name\":\"豆豆\",\"species\":\"犬\",\"ownerName\":\"倪先生\"}",
                        MediaType.APPLICATION_JSON));
    }

    private void stubDoctorOk(long doctorId) {
        remote.expect(requestTo("http://doctor-service-stub/api/doctors/" + doctorId))
                .andRespond(withSuccess(
                        "{\"id\":" + doctorId + ",\"name\":\"李医生\",\"specialty\":\"内科\",\"dailyCapacity\":4}",
                        MediaType.APPLICATION_JSON));
    }

    private CreateAppointmentRequest request(long petId, long doctorId) {
        return new CreateAppointmentRequest(petId, doctorId, T1, T2, "食欲不振，需要检查");
    }

    @Test
    void contextLoads() {
        assertThat(appointmentRepository).isNotNull();
    }

    @Test
    void createAppointmentWithRemoteValidation() {
        stubPetOk(1);
        stubDoctorOk(1);

        AppointmentResponse created = appointmentService.createAppointment(request(1, 1));

        assertThat(created.id()).isNotNull();
        assertThat(created.petName()).isEqualTo("豆豆");
        assertThat(created.doctorName()).isEqualTo("李医生");
        assertThat(created.status()).isEqualTo("BOOKED");
        // 本地只存对方服务的 ID
        Appointment stored = appointmentRepository.findById(created.id()).orElseThrow();
        assertThat(stored.getPetId()).isEqualTo(1L);
        assertThat(stored.getDoctorId()).isEqualTo(1L);
        remote.verify();
    }

    @Test
    void missingPetBlocksLocalWrite() {
        remote.expect(requestTo("http://pet-service-stub/api/pets/999"))
                .andRespond(withStatus(org.springframework.http.HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> appointmentService.createAppointment(request(999, 1)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("宠物不存在");
        // 关键断言：校验失败时本地数据库没有任何写入
        assertThat(appointmentRepository.count()).isZero();
        remote.verify();
    }

    @Test
    void dependencyDownBlocksLocalWrite() {
        // pet-service 返回 500（等价于服务不可用/异常）
        remote.expect(requestTo("http://pet-service-stub/api/pets/1"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> appointmentService.createAppointment(request(1, 1)))
                .isInstanceOf(DependencyUnavailableException.class)
                .hasMessageContaining("pet-service");
        assertThat(appointmentRepository.count()).isZero();
        remote.verify();
    }

    @Test
    void overlappingSlotRejected() {
        stubPetOk(1);
        stubDoctorOk(1);
        appointmentService.createAppointment(request(1, 1));

        // 第二次预约同医生交叉时段：10:15 ~ 10:45
        remote = MockRestServiceServer.bindTo(restTemplate).build();
        stubPetOk(1);
        stubDoctorOk(1);

        assertThatThrownBy(() -> appointmentService.createAppointment(
                new CreateAppointmentRequest(1L, 1L,
                        T1.plusMinutes(15), T1.plusMinutes(45), "交叉时段")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("号源冲突");
    }

    @Test
    void cancelTwiceRejected() {
        stubPetOk(2);
        stubDoctorOk(1);
        AppointmentResponse created = appointmentService.createAppointment(request(2, 1));

        remote = MockRestServiceServer.bindTo(restTemplate).build();
        stubPetOk(2);
        stubDoctorOk(1);
        appointmentService.cancelAppointment(created.id());

        assertThatThrownBy(() -> appointmentService.cancelAppointment(created.id()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("取消");
    }

    @Test
    void missingAppointmentReturns404() {
        assertThatThrownBy(() -> appointmentService.getAppointment(424242L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
