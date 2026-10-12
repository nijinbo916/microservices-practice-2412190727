package com.zjgsu.njb.web;

import com.zjgsu.njb.dto.DoctorResponse;
import com.zjgsu.njb.service.DoctorService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 医生（号源提供方）查询接口。
 *
 * <pre>
 * GET /api/doctors        医生列表 -> 200（可按 specialty 过滤）
 * GET /api/doctors/{id}   医生详情 -> 200 / 404
 * </pre>
 */
@RestController
@RequestMapping("/api/doctors")
public class DoctorController {

    private final DoctorService doctorService;

    public DoctorController(DoctorService doctorService) {
        this.doctorService = doctorService;
    }

    /** 查询医生列表。 */
    @GetMapping
    public List<DoctorResponse> list(@RequestParam(required = false) String specialty) {
        return doctorService.listDoctors(specialty);
    }

    /** 查询单个医生。 */
    @GetMapping("/{id}")
    public DoctorResponse get(@PathVariable Long id) {
        return doctorService.getDoctor(id);
    }
}
