package com.zjgsu.njb.doctor.dto;

import com.zjgsu.njb.doctor.domain.Doctor;

/**
 * 医生信息响应对象（对外 API 契约）。
 *
 * <p>appointment-service 通过 HTTP 读取该对象完成医生校验，
 * 并用 dailyCapacity 做「每日号源上限」业务规则校验。</p>
 */
public record DoctorResponse(
        Long id,
        String name,
        String specialty,
        int dailyCapacity
) {

    public static DoctorResponse from(Doctor doctor) {
        return new DoctorResponse(
                doctor.getId(),
                doctor.getName(),
                doctor.getSpecialty(),
                doctor.getDailyCapacity());
    }
}
