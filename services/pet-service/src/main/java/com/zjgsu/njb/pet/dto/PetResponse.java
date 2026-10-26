package com.zjgsu.njb.pet.dto;

import com.zjgsu.njb.pet.domain.Pet;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 宠物档案的响应对象。
 *
 * <p>该对象同时也是本服务对外 API 的契约：appointment-service 校验宠物、
 * 组装预约详情时读取的就是这些字段。</p>
 */
public record PetResponse(
        Long id,
        String name,
        String species,
        String breed,
        String ownerName,
        String ownerPhone,
        LocalDate birthday,
        BigDecimal weightKg,
        String allergyHistory,
        LocalDateTime createdAt
) {

    public static PetResponse from(Pet pet) {
        return new PetResponse(
                pet.getId(),
                pet.getName(),
                pet.getSpecies(),
                pet.getBreed(),
                pet.getOwnerName(),
                pet.getOwnerPhone(),
                pet.getBirthday(),
                pet.getWeightKg(),
                pet.getAllergyHistory(),
                pet.getCreatedAt());
    }
}
