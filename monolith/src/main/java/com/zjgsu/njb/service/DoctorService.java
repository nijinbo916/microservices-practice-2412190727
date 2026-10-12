package com.zjgsu.njb.service;

import com.zjgsu.njb.domain.Doctor;
import com.zjgsu.njb.dto.DoctorResponse;
import com.zjgsu.njb.exception.ResourceNotFoundException;
import com.zjgsu.njb.repository.DoctorRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 医生业务服务：提供排班号源相关的医生查询。
 */
@Service
public class DoctorService {

    private final DoctorRepository doctorRepository;

    public DoctorService(DoctorRepository doctorRepository) {
        this.doctorRepository = doctorRepository;
    }

    /** 查询医生列表，可按擅长方向过滤。 */
    @Transactional(readOnly = true)
    public List<DoctorResponse> listDoctors(String specialty) {
        List<Doctor> doctors = (specialty != null && !specialty.isBlank())
                ? doctorRepository.findBySpecialtyOrderByIdAsc(specialty.trim())
                : doctorRepository.findAll();
        return doctors.stream().map(DoctorResponse::from).toList();
    }

    /** 按 id 查询医生，不存在时抛 404。 */
    @Transactional(readOnly = true)
    public DoctorResponse getDoctor(Long id) {
        return DoctorResponse.from(doctorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("医生不存在：id=" + id)));
    }
}
