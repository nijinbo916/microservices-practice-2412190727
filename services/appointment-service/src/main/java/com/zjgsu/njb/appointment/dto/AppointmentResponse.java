package com.zjgsu.njb.appointment.dto;

import com.zjgsu.njb.appointment.domain.Appointment;
import com.zjgsu.njb.appointment.remote.DoctorView;
import com.zjgsu.njb.appointment.remote.PetView;
import java.time.LocalDateTime;

/**
 * 预约的响应对象。
 *
 * <p>petName / doctorName 等展示字段来自对 pet-service、doctor-service 的 HTTP 查询；
 * 远端不可用时由 Service 层传入降级占位文本，预约本体照常返回。</p>
 */
public record AppointmentResponse(
        Long id,
        Long petId,
        String petName,
        String petSpecies,
        String ownerName,
        Long doctorId,
        String doctorName,
        String doctorSpecialty,
        LocalDateTime slotStart,
        LocalDateTime slotEnd,
        String status,
        String reason,
        LocalDateTime createdAt
) {

    private static final String UNAVAILABLE = "（远端服务暂不可用）";

    /** 组装响应：预约本体 + 两个远端视图（可为 null，自动降级）。 */
    public static AppointmentResponse from(Appointment a, PetView pet, DoctorView doctor) {
        return new AppointmentResponse(
                a.getId(),
                a.getPetId(),
                pet != null ? pet.name() : UNAVAILABLE,
                pet != null ? pet.species() : UNAVAILABLE,
                pet != null ? pet.ownerName() : UNAVAILABLE,
                a.getDoctorId(),
                doctor != null ? doctor.name() : UNAVAILABLE,
                doctor != null ? doctor.specialty() : UNAVAILABLE,
                a.getSlotStart(),
                a.getSlotEnd(),
                a.getStatus().name(),
                a.getReason(),
                a.getCreatedAt());
    }
}
