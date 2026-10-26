package com.zjgsu.njb.doctor.service;

import com.zjgsu.njb.doctor.domain.Doctor;
import com.zjgsu.njb.doctor.dto.DoctorResponse;
import com.zjgsu.njb.doctor.exception.ResourceNotFoundException;
import com.zjgsu.njb.doctor.repository.DoctorRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 医生主数据业务服务。
 *
 * <p>医生数据由诊所维护、低频变更、多方只读，本服务只做查询与存在性校验，
 * 本周不开放写接口（主数据变更走 DBA / 管理通道），种子数据由启动 SQL 幂等灌入。</p>
 */
@Service
public class DoctorService {

    private final DoctorRepository doctorRepository;

    public DoctorService(DoctorRepository doctorRepository) {
        this.doctorRepository = doctorRepository;
    }

    /** 查询医生列表。 */
    @Transactional(readOnly = true)
    public List<DoctorResponse> listDoctors() {
        return doctorRepository.findAll().stream()
                .map(DoctorResponse::from)
                .sorted(java.util.Comparator.comparingLong(DoctorResponse::id))
                .toList();
    }

    /** 查询单个医生，不存在时抛 404。 */
    @Transactional(readOnly = true)
    public DoctorResponse getDoctor(Long id) {
        return DoctorResponse.from(requireDoctor(id));
    }

    /**
     * 获取实体。
     *
     * @throws ResourceNotFoundException 医生不存在
     */
    @Transactional(readOnly = true)
    public Doctor requireDoctor(Long id) {
        return doctorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("医生不存在：id=" + id));
    }
}
