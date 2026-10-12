package com.zjgsu.njb.repository;

import com.zjgsu.njb.domain.Appointment;
import com.zjgsu.njb.domain.AppointmentStatus;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 预约数据访问接口。
 *
 * <p>除了继承自 {@link JpaRepository} 的基础 CRUD，本接口还用 JPQL 手写了两条
 * 业务查询，直接支撑 Service 层的号源规则校验。</p>
 */
public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    /**
     * 统计某医生在指定时段内已占用号源的预约数量（时段重叠判断）。
     *
     * <p>重叠条件：{@code 已有.slotStart < 新.slotEnd 且 已有.slotEnd > 新.slotStart}。
     * 只有 BOOKED / CHECKED_IN 状态才算占用号源。</p>
     */
    @Query("""
            select count(a) from Appointment a
            where a.doctor.id = :doctorId
              and a.status in :statuses
              and a.slotStart < :slotEnd
              and a.slotEnd > :slotStart
            """)
    long countOverlapping(@Param("doctorId") Long doctorId,
                          @Param("slotStart") LocalDateTime slotStart,
                          @Param("slotEnd") LocalDateTime slotEnd,
                          @Param("statuses") Collection<AppointmentStatus> statuses);

    /**
     * 统计某医生在指定自然日内已占用的号源数量，用于校验「每日号源上限」。
     */
    @Query("""
            select count(a) from Appointment a
            where a.doctor.id = :doctorId
              and a.status in :statuses
              and a.slotStart >= :dayStart
              and a.slotStart < :dayEnd
            """)
    long countByDoctorOnDay(@Param("doctorId") Long doctorId,
                            @Param("dayStart") LocalDateTime dayStart,
                            @Param("dayEnd") LocalDateTime dayEnd,
                            @Param("statuses") Collection<AppointmentStatus> statuses);

    /** 查询全部预约，并一次性取出关联的宠物与医生，避免 N+1 查询。 */
    @Query("select a from Appointment a join fetch a.pet join fetch a.doctor order by a.slotStart asc")
    List<Appointment> findAllWithDetails();

    /** 查询某个宠物的全部预约。 */
    @Query("""
            select a from Appointment a join fetch a.pet join fetch a.doctor
            where a.pet.id = :petId order by a.slotStart asc
            """)
    List<Appointment> findByPetIdWithDetails(@Param("petId") Long petId);

    /** 查询某个医生的全部预约。 */
    @Query("""
            select a from Appointment a join fetch a.pet join fetch a.doctor
            where a.doctor.id = :doctorId order by a.slotStart asc
            """)
    List<Appointment> findByDoctorIdWithDetails(@Param("doctorId") Long doctorId);

    /** 查询单条预约详情（含关联对象）。 */
    @Query("select a from Appointment a join fetch a.pet join fetch a.doctor where a.id = :id")
    Optional<Appointment> findByIdWithDetails(@Param("id") Long id);
}
