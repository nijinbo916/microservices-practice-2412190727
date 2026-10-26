package com.zjgsu.njb.appointment.remote;

/**
 * doctor-service 返回的医生视图（含每日号源上限，供规则校验使用）。
 */
public record DoctorView(
        Long id,
        String name,
        String specialty,
        int dailyCapacity
) {
}
