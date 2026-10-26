package com.zjgsu.njb.pet;

import static org.assertj.core.api.Assertions.assertThat;

import com.zjgsu.njb.pet.domain.Pet;
import com.zjgsu.njb.pet.repository.PetRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * pet-service 仓储层测试：验证宠物档案的持久化与查询（H2 内存库，可重复）。
 */
@SpringBootTest
@ActiveProfiles("test")
class PetRepositoryTest {

    @Autowired
    private PetRepository petRepository;

    @Test
    void contextLoads() {
        // Spring 上下文与数据源装配成功
        assertThat(petRepository).isNotNull();
    }

    @Test
    void saveAndFindPet() {
        Pet saved = petRepository.save(new Pet(
                "豆豆", "犬", "金毛", "倪先生", "13800000001",
                LocalDate.of(2021, 5, 20), new BigDecimal("28.50"), "青霉素过敏"));

        assertThat(saved.getId()).isNotNull();

        Pet found = petRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getName()).isEqualTo("豆豆");
        assertThat(found.getSpecies()).isEqualTo("犬");
        assertThat(found.getWeightKg()).isEqualByComparingTo(new BigDecimal("28.50"));
        assertThat(found.getCreatedAt()).isNotNull();
    }

    @Test
    void findByOwnerName() {
        // 用唯一主人名避免与其他测试用例的数据互相干扰（H2 上下文内共享）
        String owner = "测试主人" + System.nanoTime();
        petRepository.save(new Pet("咪咪", "猫", "英短", owner, "13800000002",
                null, new BigDecimal("4.20"), null));
        petRepository.save(new Pet("旺财", "犬", "田园犬", owner, "13800000001",
                null, new BigDecimal("15.00"), null));
        petRepository.save(new Pet("豆豆", "犬", "金毛", "其他主人", "13800000003",
                null, new BigDecimal("28.50"), null));

        List<Pet> owned = petRepository.findByOwnerNameContainingIgnoreCaseOrderByIdAsc(owner);
        assertThat(owned).hasSize(2);
        assertThat(owned.get(0).getName()).isEqualTo("咪咪");
        assertThat(owned.get(1).getName()).isEqualTo("旺财");
    }
}
