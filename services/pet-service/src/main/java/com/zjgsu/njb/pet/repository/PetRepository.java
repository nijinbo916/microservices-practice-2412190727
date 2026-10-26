package com.zjgsu.njb.pet.repository;

import com.zjgsu.njb.pet.domain.Pet;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 宠物档案数据访问接口（只操作本服务的 pet 表）。
 */
public interface PetRepository extends JpaRepository<Pet, Long> {

    /** 按主人姓名模糊查询（忽略大小写）。 */
    List<Pet> findByOwnerNameContainingIgnoreCaseOrderByIdAsc(String ownerName);

    /** 按物种查询。 */
    List<Pet> findBySpeciesOrderByIdAsc(String species);
}
