package com.zjgsu.njb.dto;

import com.zjgsu.njb.domain.Doctor;

/**
 * 医生信息响应对象（用于预约时选择接诊医生）。
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
