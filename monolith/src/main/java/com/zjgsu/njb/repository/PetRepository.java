package com.zjgsu.njb.repository;

import com.zjgsu.njb.domain.Pet;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 宠物档案数据访问接口。
 *
 * <p>继承 {@link JpaRepository} 后即获得 save / findById / findAll / delete 等
 * 常用持久化能力，无需手写实现。</p>
 */
public interface PetRepository extends JpaRepository<Pet, Long> {

    /** 按主人姓名模糊查询（忽略大小写），用于前台查找名下的宠物。 */
    List<Pet> findByOwnerNameContainingIgnoreCaseOrderByIdAsc(String ownerName);

    /** 按物种查询，例如列出全部犬类。 */
    List<Pet> findBySpeciesOrderByIdAsc(String species);
}
