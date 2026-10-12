package com.zjgsu.njb.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * 预约实体 —— 业务主流程的入口。
 *
 * <p>一条预约把「谁来看病」（{@link Pet}）、「谁来接诊」（{@link Doctor}）和
 * 「什么时候来」（{@code slotStart} / {@code slotEnd}）三件事绑在一起。</p>
 */
@Entity
@Table(name = "appointment")
public class Appointment {

    /** 预约唯一标识。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 就诊宠物，多对一关联 pet 表。 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pet_id", nullable = false)
    private Pet pet;

    /** 接诊医生，多对一关联 doctor 表。 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "doctor_id", nullable = false)
    private Doctor doctor;

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

    public Appointment(Pet pet, Doctor doctor, LocalDateTime slotStart,
                       LocalDateTime slotEnd, String reason) {
        this.pet = pet;
        this.doctor = doctor;
        this.slotStart = slotStart;
        this.slotEnd = slotEnd;
        this.status = AppointmentStatus.BOOKED;
        this.reason = reason;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Pet getPet() {
        return pet;
    }

    public void setPet(Pet pet) {
        this.pet = pet;
    }

    public Doctor getDoctor() {
        return doctor;
    }

    public void setDoctor(Doctor doctor) {
        this.doctor = doctor;
    }

    public LocalDateTime getSlotStart() {
        return slotStart;
    }

    public void setSlotStart(LocalDateTime slotStart) {
        this.slotStart = slotStart;
    }

    public LocalDateTime getSlotEnd() {
        return slotEnd;
    }

    public void setSlotEnd(LocalDateTime slotEnd) {
        this.slotEnd = slotEnd;
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

    public void setReason(String reason) {
        this.reason = reason;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
