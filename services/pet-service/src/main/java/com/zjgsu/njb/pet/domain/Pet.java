package com.zjgsu.njb.pet.domain;

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
 * 宠物档案实体（pet-service 独有数据）。
 *
 * <p>预约、病历等其他服务需要宠物信息时，只能通过本服务的 HTTP API 获取，
 * 它们的数据库里最多保存本服务的宠物 ID，不允许出现 pet 表的副本或外键。</p>
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

    /** 品种，例如 金毛 / 英短。可为空。 */
    @Column(name = "breed", length = 50)
    private String breed;

    /** 主人姓名。 */
    @Column(name = "owner_name", nullable = false, length = 50)
    private String ownerName;

    /** 主人联系电话。 */
    @Column(name = "owner_phone", length = 20)
    private String ownerPhone;

    /** 出生日期。可为空。 */
    @Column(name = "birthday")
    private LocalDate birthday;

    /** 体重（千克）。 */
    @Column(name = "weight_kg", precision = 5, scale = 2)
    private BigDecimal weightKg;

    /** 过敏史。 */
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

    public String getSpecies() {
        return species;
    }

    public String getBreed() {
        return breed;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public String getOwnerPhone() {
        return ownerPhone;
    }

    public LocalDate getBirthday() {
        return birthday;
    }

    public BigDecimal getWeightKg() {
        return weightKg;
    }

    public String getAllergyHistory() {
        return allergyHistory;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
