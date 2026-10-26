package com.zjgsu.njb.appointment.repository;

import com.zjgsu.njb.appointment.domain.Appointment;
import com.zjgsu.njb.appointment.domain.AppointmentStatus;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 预约数据访问接口（只操作本服务的 appointment 表）。
 *
 * <p>号源规则的两条核心查询与单体版一致，只是关联对象从实体引用
 * 换成了本表的 doctor_id 列 —— 查询永远不离开本服务自己的数据库。</p>
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
            where a.doctorId = :doctorId
              and a.status in :statuses
              and a.slotStart < :slotEnd
              and a.slotEnd > :slotStart
            """)
    long countOverlapping(@Param("doctorId") Long doctorId,
                          @Param("slotStart") LocalDateTime slotStart,
                          @Param("slotEnd") LocalDateTime slotEnd,
                          @Param("statuses") Collection<AppointmentStatus> statuses);

    /** 统计某医生在指定自然日内已占用的号源数量，用于校验「每日号源上限」。 */
    @Query("""
            select count(a) from Appointment a
            where a.doctorId = :doctorId
              and a.status in :statuses
              and a.slotStart >= :dayStart
              and a.slotStart < :dayEnd
            """)
    long countByDoctorOnDay(@Param("doctorId") Long doctorId,
                            @Param("dayStart") LocalDateTime dayStart,
                            @Param("dayEnd") LocalDateTime dayEnd,
                            @Param("statuses") Collection<AppointmentStatus> statuses);

    /** 查询全部预约。 */
    List<Appointment> findAllByOrderBySlotStartAsc();

    /** 查询某个宠物的全部预约。 */
    List<Appointment> findByPetIdOrderBySlotStartAsc(Long petId);

    /** 查询某个医生的全部预约。 */
    List<Appointment> findByDoctorIdOrderBySlotStartAsc(Long doctorId);

    /** 查询单条预约详情。 */
    Optional<Appointment> findById(Long id);
}
