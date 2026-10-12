package com.zjgsu.njb.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.zjgsu.njb.domain.Pet;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * 宠物 Repository 读写测试。
 *
 * <p>验证 JPA 持久化确实落到数据库：save 之后能按主键查回、自定义派生查询可用。
 * 使用 H2 内存库，测试方法结束后事务回滚，可重复执行。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("PetRepository 读写")
class PetRepositoryTest {

    @Autowired
    private PetRepository petRepository;

    private Pet samplePet(String name, String ownerName, String species) {
        return new Pet(name, species, "金毛", ownerName, "13800000001",
                LocalDate.of(2021, 5, 20), new BigDecimal("28.50"), "青霉素过敏");
    }

    @Test
    @DisplayName("save 后可按主键查回，字段完整")
    void saveThenFindById() {
        Pet saved = petRepository.save(samplePet("豆豆", "倪先生", "犬"));

        assertThat(saved.getId()).isNotNull();

        assertThat(petRepository.findById(saved.getId()))
                .isPresent()
                .get()
                .satisfies(pet -> {
                    assertThat(pet.getName()).isEqualTo("豆豆");
                    assertThat(pet.getSpecies()).isEqualTo("犬");
                    assertThat(pet.getBreed()).isEqualTo("金毛");
                    assertThat(pet.getOwnerName()).isEqualTo("倪先生");
                    assertThat(pet.getOwnerPhone()).isEqualTo("13800000001");
                    assertThat(pet.getWeightKg()).isEqualByComparingTo("28.50");
                    assertThat(pet.getAllergyHistory()).isEqualTo("青霉素过敏");
                    assertThat(pet.getCreatedAt()).isNotNull();
                });
    }

    @Test
    @DisplayName("count / delete 生效")
    void countAndDelete() {
        long before = petRepository.count();
        Pet saved = petRepository.save(samplePet("球球", "王女士", "犬"));
        assertThat(petRepository.count()).isEqualTo(before + 1);

        petRepository.deleteById(saved.getId());
        assertThat(petRepository.findById(saved.getId())).isEmpty();
        assertThat(petRepository.count()).isEqualTo(before);
    }

    @Test
    @DisplayName("按主人姓名模糊查询命中多条")
    void findByOwnerNameContaining() {
        petRepository.save(samplePet("豆豆", "倪先生", "犬"));
        petRepository.save(samplePet("咪咪", "倪先生", "猫"));
        petRepository.save(samplePet("花花", "李女士", "兔"));

        List<Pet> found = petRepository.findByOwnerNameContainingIgnoreCaseOrderByIdAsc("倪先生");

        assertThat(found).hasSize(2);
        assertThat(found).extracting(Pet::getName).containsExactly("豆豆", "咪咪");
    }
}
