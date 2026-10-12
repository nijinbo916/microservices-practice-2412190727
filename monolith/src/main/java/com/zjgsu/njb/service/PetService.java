package com.zjgsu.njb.service;

import com.zjgsu.njb.domain.Pet;
import com.zjgsu.njb.dto.CreatePetRequest;
import com.zjgsu.njb.dto.PetResponse;
import com.zjgsu.njb.exception.ResourceNotFoundException;
import com.zjgsu.njb.repository.PetRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 宠物档案业务服务。
 *
 * <p>负责宠物建档与查询；不含任何 HTTP 相关代码，也不直接拼装 SQL，
 * 数据访问一律通过 {@link PetRepository} 完成。</p>
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
     * 获取实体（供其它 Service 复用）。
     *
     * @throws ResourceNotFoundException 宠物不存在
     */
    @Transactional(readOnly = true)
    public Pet requirePet(Long id) {
        return petRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("宠物不存在：id=" + id));
    }
}
