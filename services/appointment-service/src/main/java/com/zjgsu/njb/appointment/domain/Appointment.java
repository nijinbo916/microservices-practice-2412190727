package com.zjgsu.njb.appointment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * 预约实体（appointment-service 独有数据）。
 *
 * <p><b>微服务拆分的关键点：</b>与单体的 {@code @ManyToOne Pet/Doctor} 不同，
 * 本实体不再持有宠物、医生实体引用，也没有任何跨库外键 ——
 * 只保存两个服务的主键 ID（petId / doctorId，普通 BIGINT 列，即「逻辑外键」）。
 * 宠物、医生的详细信息需要时通过 HTTP API 向对应服务获取。</p>
 */
@Entity
@Table(name = "appointment")
public class Appointment {

    /** 预约唯一标识。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 就诊宠物的 ID —— pet-service 的主键（逻辑外键，无数据库约束）。 */
    @Column(name = "pet_id", nullable = false)
    private Long petId;

    /** 接诊医生的 ID —— doctor-service 的主键（逻辑外键，无数据库约束）。 */
    @Column(name = "doctor_id", nullable = false)
    private Long doctorId;

    /** 预约时段开始时间。 */
    @Column(name = "slot_start", nullable = false)
    private LocalDateTime slotStart;

    /** 预约时段结束时间。 */
    @Column(name = "slot_end", nullable = false)
    private LocalDateTime slotEnd;

    /** 预约状态。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AppointmentStatus status;

    /** 就诊原因（主人自述）。 */
    @Column(name = "reason", length = 255)
    private String reason;

    /** 创建时间。 */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected Appointment() {
        // JPA 要求的无参构造
    }

    public Appointment(Long petId, Long doctorId, LocalDateTime slotStart,
                       LocalDateTime slotEnd, String reason) {
        this.petId = petId;
        this.doctorId = doctorId;
        this.slotStart = slotStart;
        this.slotEnd = slotEnd;
        this.status = AppointmentStatus.BOOKED;
        this.reason = reason;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Long getPetId() {
        return petId;
    }

    public Long getDoctorId() {
        return doctorId;
    }

    public LocalDateTime getSlotStart() {
        return slotStart;
    }

    public LocalDateTime getSlotEnd() {
        return slotEnd;
    }

    public AppointmentStatus getStatus() {
        return status;
    }

    public void setStatus(AppointmentStatus status) {
        this.status = status;
    }

    public String getReason() {
        return reason;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
