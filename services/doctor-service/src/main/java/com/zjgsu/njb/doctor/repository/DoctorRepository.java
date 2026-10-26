package com.zjgsu.njb.doctor.repository;

import com.zjgsu.njb.doctor.domain.Doctor;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 医生数据访问接口（只操作本服务的 doctor 表）。
 */
public interface DoctorRepository extends JpaRepository<Doctor, Long> {
}
