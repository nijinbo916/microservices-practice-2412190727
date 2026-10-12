package com.zjgsu.njb.dto;

import com.zjgsu.njb.domain.Pet;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 宠物档案的响应对象。
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
