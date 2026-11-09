package com.zjgsu.njb.appointment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import com.zjgsu.njb.appointment.domain.Appointment;
import com.zjgsu.njb.appointment.dto.AppointmentResponse;
import com.zjgsu.njb.appointment.dto.CreateAppointmentRequest;
import com.zjgsu.njb.appointment.exception.BusinessRuleException;
import com.zjgsu.njb.appointment.exception.DependencyUnavailableException;
import com.zjgsu.njb.appointment.exception.ResourceNotFoundException;
import com.zjgsu.njb.appointment.remote.DoctorFeignClient;
import com.zjgsu.njb.appointment.remote.DoctorView;
import com.zjgsu.njb.appointment.remote.PetFeignClient;
import com.zjgsu.njb.appointment.remote.PetView;
import com.zjgsu.njb.appointment.repository.AppointmentRepository;
import com.zjgsu.njb.appointment.service.AppointmentService;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * appointment-service 业务规则 + 跨服务调用测试（H2 + Mockito 桩 OpenFeign 客户端）。
 *
 * <p>第 08 周起跨服务调用走 OpenFeign：测试直接用 {@code @MockitoBean}
 * 替换 {@link PetFeignClient} / {@link DoctorFeignClient}，不连接真实 Nacos，
 * 也不会发起真实 HTTP 请求；失败场景抛出的 {@code FeignException}
 * 由 {@code FeignStubs.error(status)} 构造，与容器内真实失败类型一致。</p>
 *
 * <p>覆盖业务规则（时段冲突、每日上限）以及
 * 「依赖资源不存在 / 依赖服务不可用时绝不写入本地数据」的关键语义。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class AppointmentServiceTest {

    // 预约时间必须晚于当前时间（DTO @Future 校验），所以用动态未来时间，避免测试数据「过期」
    private static final LocalDateTime T1 =
            LocalDateTime.now().truncatedTo(ChronoUnit.HOURS).plusDays(3);
    private static final LocalDateTime T2 = T1.plusMinutes(30);

    /** 替换真实 Feign 客户端：不连接 Nacos，不发真实请求。 */
    @MockitoBean
    private PetFeignClient petFeignClient;

    @MockitoBean
    private DoctorFeignClient doctorFeignClient;

    @Autowired
    private AppointmentService appointmentService;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @BeforeEach
    void setUp() {
        appointmentRepository.deleteAll();
    }

    private void stubPetOk(long petId) {
        when(petFeignClient.getPet(petId)).thenReturn(
                new PetView(petId, "豆豆", "犬", "倪先生"));
    }

    private void stubDoctorOk(long doctorId) {
        when(doctorFeignClient.getDoctor(doctorId)).thenReturn(
                new DoctorView(doctorId, "李医生", "内科", 4));
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
    }

    @Test
    void missingPetBlocksLocalWrite() {
        // Feign 收到远端 404：与容器内真实 404 同类型的 FeignException.NotFound
        when(petFeignClient.getPet(999L)).thenThrow(FeignStubs.error(404, "getPet"));

        assertThatThrownBy(() -> appointmentService.createAppointment(request(999, 1)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("宠物不存在");
        // 关键断言：校验失败时本地数据库没有任何写入
        assertThat(appointmentRepository.count()).isZero();
    }

    @Test
    void dependencyDownBlocksLocalWrite() {
        // pet-service 返回 500（等价于服务不可用/异常）
        when(petFeignClient.getPet(1L)).thenThrow(FeignStubs.error(500, "getPet"));

        assertThatThrownBy(() -> appointmentService.createAppointment(request(1, 1)))
                .isInstanceOf(DependencyUnavailableException.class)
                .hasMessageContaining("pet-service");
        assertThat(appointmentRepository.count()).isZero();
    }

    @Test
    void overlappingSlotRejected() {
        stubPetOk(1);
        stubDoctorOk(1);
        appointmentService.createAppointment(request(1, 1));

        // 第二次预约同医生交叉时段：10:15 ~ 10:45
        assertThatThrownBy(() -> appointmentService.createAppointment(
                new CreateAppointmentRequest(1L, 1L,
                        T1.plusMinutes(15), T1.plusMinutes(45), "交叉时段")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("号源冲突");
    }

    @Test
    void dailyCapacityLimitRejected() {
        stubPetOk(1);
        stubDoctorOk(1); // dailyCapacity = 4
        // 同一天内 4 个互不重叠的整点时段，恰好填满每日上限
        for (int i = 0; i < 4; i++) {
            LocalDateTime start = T1.plusHours(i);
            appointmentService.createAppointment(
                    new CreateAppointmentRequest(1L, 1L, start, start.plusMinutes(30), "第 " + i + " 次"));
        }
        // 第 5 次：仍是同一天，当日预约数已达上限
        assertThatThrownBy(() -> appointmentService.createAppointment(
                new CreateAppointmentRequest(1L, 1L, T1.plusHours(4), T1.plusHours(4).plusMinutes(30), "超限")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("号源已满");
    }

    @Test
    void cancelTwiceRejected() {
        stubPetOk(2);
        stubDoctorOk(1);
        AppointmentResponse created = appointmentService.createAppointment(request(2, 1));

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

    @Test
    void listDegradesWhenRemoteDown() {
        stubPetOk(1);
        stubDoctorOk(1);
        AppointmentResponse created = appointmentService.createAppointment(request(1, 1));
        when(petFeignClient.getPet(anyLong())).thenThrow(FeignStubs.error(503, "getPet"));

        // 弱依赖降级：列表仍能返回，只是 petName 为 null
        var list = appointmentService.listAppointments(null, null);
        assertThat(list).extracting(AppointmentResponse::id).containsExactly(created.id());
    }
}
