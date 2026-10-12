package com.zjgsu.njb.web;

import com.zjgsu.njb.dto.AppointmentResponse;
import com.zjgsu.njb.dto.CreateAppointmentRequest;
import com.zjgsu.njb.service.AppointmentService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 预约 REST 接口 —— 本周核心业务流程的对外入口。
 *
 * <pre>
 * POST /api/appointments               创建预约  -> 201 / 400 / 404 / 409
 * GET  /api/appointments               预约列表  -> 200（可按 petId / doctorId 过滤）
 * GET  /api/appointments/{id}          预约详情  -> 200 / 404
 * POST /api/appointments/{id}/cancel   取消预约  -> 200 / 404 / 409
 * </pre>
 */
@RestController
@RequestMapping("/api/appointments")
public class AppointmentController {

    private final AppointmentService appointmentService;

    public AppointmentController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    /** 创建预约：号源冲突返回 409，宠物/医生不存在返回 404，参数非法返回 400。 */
    @PostMapping
    public ResponseEntity<AppointmentResponse> create(
            @Valid @RequestBody CreateAppointmentRequest request) {
        AppointmentResponse created = appointmentService.createAppointment(request);
        return ResponseEntity.created(URI.create("/api/appointments/" + created.id())).body(created);
    }

    /** 查询预约列表，支持按宠物或医生过滤。 */
    @GetMapping
    public List<AppointmentResponse> list(@RequestParam(required = false) Long petId,
                                          @RequestParam(required = false) Long doctorId) {
        return appointmentService.listAppointments(petId, doctorId);
    }

    /** 查询单条预约详情。 */
    @GetMapping("/{id}")
    public AppointmentResponse get(@PathVariable Long id) {
        return appointmentService.getAppointment(id);
    }

    /** 取消预约。 */
    @PostMapping("/{id}/cancel")
    public AppointmentResponse cancel(@PathVariable Long id) {
        return appointmentService.cancelAppointment(id);
    }
}
