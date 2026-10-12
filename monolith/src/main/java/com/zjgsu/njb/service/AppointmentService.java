package com.zjgsu.njb.service;

import com.zjgsu.njb.domain.Appointment;
import com.zjgsu.njb.domain.AppointmentStatus;
import com.zjgsu.njb.domain.Doctor;
import com.zjgsu.njb.domain.Pet;
import com.zjgsu.njb.dto.AppointmentResponse;
import com.zjgsu.njb.dto.CreateAppointmentRequest;
import com.zjgsu.njb.exception.BusinessRuleException;
import com.zjgsu.njb.exception.ResourceNotFoundException;
import com.zjgsu.njb.repository.AppointmentRepository;
import com.zjgsu.njb.repository.DoctorRepository;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 预约业务服务 —— 本周核心业务流程的规则实现处。
 *
 * <h3>业务规则（全部在 Service 中校验，Controller 不承担业务判断）</h3>
 * <ol>
 *   <li><b>时段自身合法</b>：结束时间必须晚于开始时间，否则 400。</li>
 *   <li><b>号源不可冲突</b>：同一医生在同一时段不能有两条有效预约（防号源超卖），冲突返回 409。</li>
 *   <li><b>每日号源上限</b>：某医生当日的有效预约数不得超过其 {@code dailyCapacity}，超限返回 409。</li>
 *   <li><b>状态流转合法</b>：已完成或已取消的预约不能再次取消，返回 409。</li>
 * </ol>
 */
@Service
public class AppointmentService {

    /** 占用号源的预约状态：只有「已预约」和「已登记」会占坑。 */
    private static final List<AppointmentStatus> SLOT_OCCUPYING_STATUSES =
            List.of(AppointmentStatus.BOOKED, AppointmentStatus.CHECKED_IN);

    private final AppointmentRepository appointmentRepository;
    private final DoctorRepository doctorRepository;
    private final PetService petService;

    public AppointmentService(AppointmentRepository appointmentRepository,
                              DoctorRepository doctorRepository,
                              PetService petService) {
        this.appointmentRepository = appointmentRepository;
        this.doctorRepository = doctorRepository;
        this.petService = petService;
    }

    /**
     * 创建预约：这是本周实现的核心业务流程入口。
     *
     * @throws IllegalArgumentException 时段起止时间不合法（映射为 400）
     * @throws ResourceNotFoundException 宠物或医生不存在（映射为 404）
     * @throws BusinessRuleException 号源冲突或超出每日号源上限（映射为 409）
     */
    @Transactional
    public AppointmentResponse createAppointment(CreateAppointmentRequest request) {
        // 规则 1：时段自身必须合法
        if (!request.slotEnd().isAfter(request.slotStart())) {
            throw new IllegalArgumentException("预约结束时间必须晚于开始时间");
        }

        Pet pet = petService.requirePet(request.petId());
        Doctor doctor = doctorRepository.findById(request.doctorId())
                .orElseThrow(() -> new ResourceNotFoundException("医生不存在：id=" + request.doctorId()));

        // 规则 2：同一医生同一时段不可重复预约 —— 防号源超卖
        long overlapped = appointmentRepository.countOverlapping(
                doctor.getId(), request.slotStart(), request.slotEnd(), SLOT_OCCUPYING_STATUSES);
        if (overlapped > 0) {
            throw new BusinessRuleException(
                    "号源冲突：医生「" + doctor.getName() + "」在 "
                            + request.slotStart() + " ~ " + request.slotEnd() + " 时段已有预约");
        }

        // 规则 3：某医生当日的有效预约数不得超过每日号源上限
        LocalDate day = request.slotStart().toLocalDate();
        long bookedOnDay = appointmentRepository.countByDoctorOnDay(
                doctor.getId(),
                day.atStartOfDay(),
                day.plusDays(1).atStartOfDay(),
                SLOT_OCCUPYING_STATUSES);
        if (bookedOnDay >= doctor.getDailyCapacity()) {
            throw new BusinessRuleException(
                    "号源已满：医生「" + doctor.getName() + "」在 " + day
                            + " 的预约数已达每日上限 " + doctor.getDailyCapacity() + " 个");
        }

        Appointment appointment = new Appointment(
                pet, doctor, request.slotStart(), request.slotEnd(), request.reason());
        return AppointmentResponse.from(appointmentRepository.save(appointment));
    }

    /** 查询预约列表，可按宠物或医生过滤。 */
    @Transactional(readOnly = true)
    public List<AppointmentResponse> listAppointments(Long petId, Long doctorId) {
        List<Appointment> appointments;
        if (petId != null) {
            appointments = appointmentRepository.findByPetIdWithDetails(petId);
        } else if (doctorId != null) {
            appointments = appointmentRepository.findByDoctorIdWithDetails(doctorId);
        } else {
            appointments = appointmentRepository.findAllWithDetails();
        }
        return appointments.stream().map(AppointmentResponse::from).toList();
    }

    /** 按 id 查询预约，不存在时抛 404。 */
    @Transactional(readOnly = true)
    public AppointmentResponse getAppointment(Long id) {
        return AppointmentResponse.from(requireAppointment(id));
    }

    /**
     * 取消预约（规则 4：状态流转合法性校验）。
     *
     * @throws BusinessRuleException 已取消或已完成的预约不可再次取消（映射为 409）
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
        return AppointmentResponse.from(appointmentRepository.save(appointment));
    }

    /**
     * 获取预约实体（供其它 Service 复用）。
     *
     * @throws ResourceNotFoundException 预约不存在
     */
    @Transactional(readOnly = true)
    public Appointment requireAppointment(Long id) {
        return appointmentRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("预约不存在：id=" + id));
    }
}
