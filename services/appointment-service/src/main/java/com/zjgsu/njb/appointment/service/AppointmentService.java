package com.zjgsu.njb.appointment.service;

import com.zjgsu.njb.appointment.domain.Appointment;
import com.zjgsu.njb.appointment.domain.AppointmentStatus;
import com.zjgsu.njb.appointment.dto.AppointmentResponse;
import com.zjgsu.njb.appointment.dto.CreateAppointmentRequest;
import com.zjgsu.njb.appointment.exception.BusinessRuleException;
import com.zjgsu.njb.appointment.exception.ResourceNotFoundException;
import com.zjgsu.njb.appointment.remote.DoctorClient;
import com.zjgsu.njb.appointment.remote.DoctorView;
import com.zjgsu.njb.appointment.remote.PetClient;
import com.zjgsu.njb.appointment.remote.PetView;
import com.zjgsu.njb.appointment.repository.AppointmentRepository;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 预约业务服务 —— 号源规则的实现处 + 跨服务校验的发起方。
 *
 * <h3>创建预约的完整流程（跨服务 HTTP 协作）</h3>
 * <ol>
 *   <li>校验时段自身合法（400）；</li>
 *   <li><b>OpenFeign 按服务名调用 pet-service</b> 校验宠物（404/503 则中止，不写库）；</li>
 *   <li><b>OpenFeign 按服务名调用 doctor-service</b> 校验医生并取 dailyCapacity（中止条件同上）；</li>
 *   <li>本地号源规则：时段冲突 → 409；每日有效预约数 ≥ dailyCapacity → 409；</li>
 *   <li>落库：只存 petId / doctorId（逻辑外键），不存对方数据副本。</li>
 * </ol>
 */
@Service
public class AppointmentService {

    /** 占用号源的预约状态：只有「已预约」和「已登记」会占坑。 */
    private static final List<AppointmentStatus> SLOT_OCCUPYING_STATUSES =
            List.of(AppointmentStatus.BOOKED, AppointmentStatus.CHECKED_IN);

    private final AppointmentRepository appointmentRepository;
    private final PetClient petClient;
    private final DoctorClient doctorClient;

    public AppointmentService(AppointmentRepository appointmentRepository,
                              PetClient petClient,
                              DoctorClient doctorClient) {
        this.appointmentRepository = appointmentRepository;
        this.petClient = petClient;
        this.doctorClient = doctorClient;
    }

    /** 创建预约（核心跨服务业务流程入口）。 */
    @Transactional
    public AppointmentResponse createAppointment(CreateAppointmentRequest request) {
        // 规则 1：时段自身必须合法
        if (!request.slotEnd().isAfter(request.slotStart())) {
            throw new IllegalArgumentException("预约结束时间必须晚于开始时间");
        }

        // 步骤 2/3：跨服务 HTTP 校验（强依赖，失败即中止，绝不跳过校验写入本地数据）
        PetView pet = petClient.requirePet(request.petId());
        DoctorView doctor = doctorClient.requireDoctor(request.doctorId());

        // 规则 2：同一医生同一时段不可重复预约 —— 防号源超卖（查本服务自己的表）
        long overlapped = appointmentRepository.countOverlapping(
                request.doctorId(), request.slotStart(), request.slotEnd(), SLOT_OCCUPYING_STATUSES);
        if (overlapped > 0) {
            throw new BusinessRuleException(
                    "号源冲突：医生「" + doctor.name() + "」在 "
                            + request.slotStart() + " ~ " + request.slotEnd() + " 时段已有预约");
        }

        // 规则 3：某医生当日的有效预约数不得超过每日号源上限（上限值来自 doctor-service）
        LocalDate day = request.slotStart().toLocalDate();
        long bookedOnDay = appointmentRepository.countByDoctorOnDay(
                request.doctorId(),
                day.atStartOfDay(),
                day.plusDays(1).atStartOfDay(),
                SLOT_OCCUPYING_STATUSES);
        if (bookedOnDay >= doctor.dailyCapacity()) {
            throw new BusinessRuleException(
                    "号源已满：医生「" + doctor.name() + "」在 " + day
                            + " 的预约数已达每日上限 " + doctor.dailyCapacity() + " 个");
        }

        // 步骤 5：落库 —— 只保存对方服务的 ID
        Appointment appointment = new Appointment(
                request.petId(), request.doctorId(),
                request.slotStart(), request.slotEnd(), request.reason());
        return AppointmentResponse.from(appointmentRepository.save(appointment), pet, doctor);
    }

    /** 查询预约列表，可按宠物或医生过滤；展示字段经远端增强，远端故障时降级。 */
    @Transactional(readOnly = true)
    public List<AppointmentResponse> listAppointments(Long petId, Long doctorId) {
        List<Appointment> appointments;
        if (petId != null) {
            appointments = appointmentRepository.findByPetIdOrderBySlotStartAsc(petId);
        } else if (doctorId != null) {
            appointments = appointmentRepository.findByDoctorIdOrderBySlotStartAsc(doctorId);
        } else {
            appointments = appointmentRepository.findAllByOrderBySlotStartAsc();
        }
        return appointments.stream().map(this::toResponse).toList();
    }

    /** 按 id 查询预约，不存在时抛 404。 */
    @Transactional(readOnly = true)
    public AppointmentResponse getAppointment(Long id) {
        return toResponse(requireAppointment(id));
    }

    /**
     * 取消预约（状态流转合法性校验）。
     *
     * @throws BusinessRuleException 已取消或已完成的预约不可再次取消（409）
     */
    @Transactional
    public AppointmentResponse cancelAppointment(Long id) {
        Appointment appointment = requireAppointment(id);
        if (appointment.getStatus() == AppointmentStatus.COMPLETED) {
            throw new BusinessRuleException("预约已完成就诊，不能取消：id=" + id);
        }
        if (appointment.getStatus() == AppointmentStatus.CANCELED) {
            throw new BusinessRuleException("该预约已是取消状态，无需重复取消：id=" + id);
        }
        appointment.setStatus(AppointmentStatus.CANCELED);
        return AppointmentResponse.from(appointmentRepository.save(appointment),
                petClient.findPet(appointment.getPetId()),
                doctorClient.findDoctor(appointment.getDoctorId()));
    }

    /** 实体 → 响应，展示字段走远端增强（弱依赖，可降级）。 */
    private AppointmentResponse toResponse(Appointment appointment) {
        return AppointmentResponse.from(appointment,
                petClient.findPet(appointment.getPetId()),
                doctorClient.findDoctor(appointment.getDoctorId()));
    }

    /**
     * 获取预约实体。
     *
     * @throws ResourceNotFoundException 预约不存在
     */
    @Transactional(readOnly = true)
    public Appointment requireAppointment(Long id) {
        return appointmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("预约不存在：id=" + id));
    }
}
