package com.zjgsu.njb.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 医生实体。
 *
 * <p>医生是号源的提供方：每个医生有固定的每日号源上限（{@code dailyCapacity}），
 * 预约时必须检查该医生当日已占用的号源是否超过上限，避免号源超卖。</p>
 */
@Entity
@Table(name = "doctor")
public class Doctor {

    /** 医生唯一标识。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 医生姓名。 */
    @Column(name = "name", nullable = false, length = 50)
    private String name;

    /** 擅长方向，例如 内科 / 外科 / 影像。 */
    @Column(name = "specialty", length = 50)
    private String specialty;

    /** 每日可接诊号源上限。 */
    @Column(name = "daily_capacity", nullable = false)
    private int dailyCapacity;

    protected Doctor() {
        // JPA 要求的无参构造
    }

    public Doctor(String name, String specialty, int dailyCapacity) {
        this.name = name;
        this.specialty = specialty;
        this.dailyCapacity = dailyCapacity;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSpecialty() {
        return specialty;
    }

    public void setSpecialty(String specialty) {
        this.specialty = specialty;
    }

    public int getDailyCapacity() {
        return dailyCapacity;
    }

    public void setDailyCapacity(int dailyCapacity) {
        this.dailyCapacity = dailyCapacity;
    }
}
