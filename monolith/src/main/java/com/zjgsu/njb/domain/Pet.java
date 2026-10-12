package com.zjgsu.njb.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 宠物档案实体。
 *
 * <p>宠物是本系统所有业务围绕的主体：预约、病历、处方、收费都必须先关联到具体的宠物。</p>
 */
@Entity
@Table(name = "pet")
public class Pet {

    /** 宠物唯一标识，数据库自增主键。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 宠物昵称。 */
    @Column(name = "name", nullable = false, length = 50)
    private String name;

    /** 物种，例如 犬 / 猫 / 兔。 */
    @Column(name = "species", nullable = false, length = 20)
    private String species;

    /** 品种，例如 金毛 / 英短。可为空，主人不一定知道。 */
    @Column(name = "breed", length = 50)
    private String breed;

    /** 主人姓名。 */
    @Column(name = "owner_name", nullable = false, length = 50)
    private String ownerName;

    /** 主人联系电话，用于就诊提醒。 */
    @Column(name = "owner_phone", length = 20)
    private String ownerPhone;

    /** 出生日期。可为空。 */
    @Column(name = "birthday")
    private LocalDate birthday;

    /** 体重（千克）。用药剂量计算依赖该字段。 */
    @Column(name = "weight_kg", precision = 5, scale = 2)
    private BigDecimal weightKg;

    /** 过敏史。开处方前医生必须确认，关系到用药安全。 */
    @Column(name = "allergy_history", length = 255)
    private String allergyHistory;

    /** 建档时间。 */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected Pet() {
        // JPA 要求的无参构造
    }

    public Pet(String name, String species, String breed, String ownerName,
               String ownerPhone, LocalDate birthday, BigDecimal weightKg, String allergyHistory) {
        this.name = name;
        this.species = species;
        this.breed = breed;
        this.ownerName = ownerName;
        this.ownerPhone = ownerPhone;
        this.birthday = birthday;
        this.weightKg = weightKg;
        this.allergyHistory = allergyHistory;
        this.createdAt = LocalDateTime.now();
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

    public String getSpecies() {
        return species;
    }

    public void setSpecies(String species) {
        this.species = species;
    }

    public String getBreed() {
        return breed;
    }

    public void setBreed(String breed) {
        this.breed = breed;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public String getOwnerPhone() {
        return ownerPhone;
    }

    public void setOwnerPhone(String ownerPhone) {
        this.ownerPhone = ownerPhone;
    }

    public LocalDate getBirthday() {
        return birthday;
    }

    public void setBirthday(LocalDate birthday) {
        this.birthday = birthday;
    }

    public BigDecimal getWeightKg() {
        return weightKg;
    }

    public void setWeightKg(BigDecimal weightKg) {
        this.weightKg = weightKg;
    }

    public String getAllergyHistory() {
        return allergyHistory;
    }

    public void setAllergyHistory(String allergyHistory) {
        this.allergyHistory = allergyHistory;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
