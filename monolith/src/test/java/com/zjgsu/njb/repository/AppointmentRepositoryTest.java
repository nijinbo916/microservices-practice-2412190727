package com.zjgsu.njb.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.zjgsu.njb.domain.Appointment;
import com.zjgsu.njb.domain.AppointmentStatus;
import com.zjgsu.njb.domain.Doctor;
import com.zjgsu.njb.domain.Pet;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * 预约 Repository 读写测试。
 *
 * <p>重点验证两件事：多对一关联（appointment → pet / doctor）能否正确落库，
 * 以及支撑业务规则的时段重叠统计查询是否准确。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("AppointmentRepository 读写与冲突统计")
class AppointmentRepositoryTest {

    private static final List<AppointmentStatus> OCCUPYING =
            List.of(AppointmentStatus.BOOKED, AppointmentStatus.CHECKED_IN);

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private PetRepository petRepository;

    @Autowired
    private DoctorRepository doctorRepository;

    private Pet newPet() {
        return petRepository.save(new Pet("豆豆", "犬", "金毛", "倪先生",
                "13800000001", LocalDate.of(2021, 5, 20), new BigDecimal("28.50"), null));
    }

    private Doctor newDoctor(int capacity) {
        return doctorRepository.save(new Doctor("测试医生", "内科", capacity));
    }

    @Test
    @DisplayName("保存预约并连同关联对象一起查回")
    void saveWithAssociations() {
        Pet pet = newPet();
        Doctor doctor = newDoctor(8);
        LocalDateTime start = LocalDateTime.now().plusDays(1).withHour(10).withMinute(0).withSecond(0).withNano(0);

        Appointment saved = appointmentRepository.save(
                new Appointment(pet, doctor, start, start.plusMinutes(30), "食欲不振"));

        assertThat(saved.getId()).isNotNull();

        assertThat(appointmentRepository.findByIdWithDetails(saved.getId()))
                .isPresent()
                .get()
                .satisfies(a -> {
                    assertThat(a.getPet().getName()).isEqualTo("豆豆");
                    assertThat(a.getDoctor().getName()).isEqualTo("测试医生");
                    assertThat(a.getStatus()).isEqualTo(AppointmentStatus.BOOKED);
                    assertThat(a.getReason()).isEqualTo("食欲不振");
                });
    }

    @Test
    @DisplayName("时段重叠统计：重叠计为 1，相邻不重叠计为 0")
    void countOverlapping() {
        Pet pet = newPet();
        Doctor doctor = newDoctor(8);
        LocalDateTime start = LocalDateTime.now().plusDays(1).withHour(10).withMinute(0).withSecond(0).withNano(0);

        appointmentRepository.save(new Appointment(pet, doctor, start, start.plusMinutes(30), "初诊"));

        // 完全落在已有时段内部 → 冲突
        assertThat(appointmentRepository.countOverlapping(
                doctor.getId(), start.plusMinutes(5), start.plusMinutes(25), OCCUPYING)).isEqualTo(1);

        // 与已有时段部分重叠 → 冲突
        assertThat(appointmentRepository.countOverlapping(
                doctor.getId(), start.plusMinutes(20), start.plusMinutes(50), OCCUPYING)).isEqualTo(1);

        // 紧接在已有时段之后（首尾相接，不重叠）→ 不冲突
        assertThat(appointmentRepository.countOverlapping(
                doctor.getId(), start.plusMinutes(30), start.plusMinutes(60), OCCUPYING)).isEqualTo(0);

        // 另一位医生同一时段 → 不冲突
        Doctor another = newDoctor(8);
        assertThat(appointmentRepository.countOverlapping(
                another.getId(), start, start.plusMinutes(30), OCCUPYING)).isEqualTo(0);
    }

    @Test
    @DisplayName("取消的预约不再占用号源")
    void canceledAppointmentDoesNotOccupySlot() {
        Pet pet = newPet();
        Doctor doctor = newDoctor(8);
        LocalDateTime start = LocalDateTime.now().plusDays(2).withHour(9).withMinute(0).withSecond(0).withNano(0);

        Appointment appointment = appointmentRepository.save(
                new Appointment(pet, doctor, start, start.plusMinutes(30), "复查"));
        appointment.setStatus(AppointmentStatus.CANCELED);
        appointmentRepository.saveAndFlush(appointment);

        assertThat(appointmentRepository.countOverlapping(
                doctor.getId(), start, start.plusMinutes(30), OCCUPYING)).isEqualTo(0);
    }

    @Test
    @DisplayName("按医生统计当日号源占用数")
    void countByDoctorOnDay() {
        Pet pet = newPet();
        Doctor doctor = newDoctor(8);
        LocalDate day = LocalDate.now().plusDays(3);
        LocalDateTime base = day.atTime(9, 0);

        appointmentRepository.save(new Appointment(pet, doctor, base, base.plusMinutes(30), "A"));
        appointmentRepository.save(new Appointment(pet, doctor, base.plusHours(1), base.plusHours(1).plusMinutes(30), "B"));
        // 次日的预约不计入当天
        appointmentRepository.save(new Appointment(pet, doctor, base.plusDays(1), base.plusDays(1).plusMinutes(30), "C"));

        assertThat(appointmentRepository.countByDoctorOnDay(
                doctor.getId(), day.atStartOfDay(), day.plusDays(1).atStartOfDay(), OCCUPYING)).isEqualTo(2);
    }
}
