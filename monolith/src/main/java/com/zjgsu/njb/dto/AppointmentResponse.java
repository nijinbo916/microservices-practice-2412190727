package com.zjgsu.njb.dto;

import com.zjgsu.njb.domain.Appointment;
import java.time.LocalDateTime;

/**
 * 预约的响应对象。
 *
 * <p>把关联的宠物、医生信息「拍平」成扁平字段返回，客户端无需二次查询，
 * 同时避免直接序列化 JPA 实体带来的懒加载问题。</p>
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

    public static AppointmentResponse from(Appointment appointment) {
        return new AppointmentResponse(
                appointment.getId(),
                appointment.getPet().getId(),
                appointment.getPet().getName(),
                appointment.getPet().getSpecies(),
                appointment.getPet().getOwnerName(),
                appointment.getDoctor().getId(),
                appointment.getDoctor().getName(),
                appointment.getDoctor().getSpecialty(),
                appointment.getSlotStart(),
                appointment.getSlotEnd(),
                appointment.getStatus().name(),
                appointment.getReason(),
                appointment.getCreatedAt());
    }
}
