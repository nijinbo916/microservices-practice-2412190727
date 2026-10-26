package com.zjgsu.njb.doctor.web;

import com.zjgsu.njb.doctor.dto.DoctorResponse;
import com.zjgsu.njb.doctor.service.DoctorService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 医生 REST 接口。
 *
 * <pre>
 * GET /api/doctors          医生列表  -&gt; 200 OK
 * GET /api/doctors/{id}     医生详情  -&gt; 200 OK / 404 Not Found
 * </pre>
 */
@RestController
@RequestMapping("/api/doctors")
public class DoctorController {

    private final DoctorService doctorService;

    public DoctorController(DoctorService doctorService) {
        this.doctorService = doctorService;
    }

    /** 医生列表（含每日号源上限）。 */
    @GetMapping
    public List<DoctorResponse> list() {
        return doctorService.listDoctors();
    }

    /** 医生详情，appointment-service 校验医生时调用。 */
    @GetMapping("/{id}")
    public DoctorResponse get(@PathVariable Long id) {
        return doctorService.getDoctor(id);
    }
}
