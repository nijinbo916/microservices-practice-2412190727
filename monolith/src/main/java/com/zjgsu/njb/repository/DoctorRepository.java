package com.zjgsu.njb.repository;

import com.zjgsu.njb.domain.Doctor;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 医生数据访问接口。
 */
public interface DoctorRepository extends JpaRepository<Doctor, Long> {

    /** 按擅长方向查询医生。 */
    List<Doctor> findBySpecialtyOrderByIdAsc(String specialty);
}
