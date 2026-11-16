package com.zjgsu.njb.pet.service;

import com.zjgsu.njb.pet.domain.Pet;
import com.zjgsu.njb.pet.dto.CreatePetRequest;
import com.zjgsu.njb.pet.dto.PetResponse;
import com.zjgsu.njb.pet.exception.ResourceNotFoundException;
import com.zjgsu.njb.pet.repository.PetRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 宠物档案业务服务。
 *
 * <p>本服务没有跨服务调用：宠物数据自成一体，其他服务（如 appointment-service）
 * 要用宠物信息必须走 HTTP API 来问，本服务不感知任何调用方。</p>
 */
@Service
public class PetService {

    private final PetRepository petRepository;

    public PetService(PetRepository petRepository) {
        this.petRepository = petRepository;
    }

    /** 为宠物建档。 */
    @Transactional
    public PetResponse createPet(CreatePetRequest request) {
        Pet pet = new Pet(
                request.name().trim(),
                request.species().trim(),
                request.breed(),
                request.ownerName().trim(),
                request.ownerPhone(),
                request.birthday(),
                request.weightKg(),
                request.allergyHistory());
        return PetResponse.from(petRepository.save(pet));
    }

    /** 查询宠物档案，可按主人姓名或物种过滤。 */
    @Transactional(readOnly = true)
    public List<PetResponse> listPets(String ownerName, String species) {
        List<Pet> pets;
        if (ownerName != null && !ownerName.isBlank()) {
            pets = petRepository.findByOwnerNameContainingIgnoreCaseOrderByIdAsc(ownerName.trim());
        } else if (species != null && !species.isBlank()) {
            pets = petRepository.findBySpeciesOrderByIdAsc(species.trim());
        } else {
            pets = petRepository.findAll();
        }
        return pets.stream().map(PetResponse::from).toList();
    }

    /** 按 id 查询宠物，不存在时抛 404。 */
    @Transactional(readOnly = true)
    public PetResponse getPet(Long id) {
        return PetResponse.from(requirePet(id));
    }

    /**
     * 删除宠物档案（第 09 周新增）。
     *
     * <p>先确认存在再删，保证「删不存在的 id」返回 404 而不是静默成功 ——
     * 静默成功会让调用方以为删掉了别的东西。</p>
     */
    @Transactional
    public void deletePet(Long id) {
        Pet pet = requirePet(id);
        petRepository.delete(pet);
    }

    /**
     * 获取实体。
     *
     * @throws ResourceNotFoundException 宠物不存在
     */
    @Transactional(readOnly = true)
    public Pet requirePet(Long id) {
        return petRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("宠物不存在：id=" + id));
    }
}
