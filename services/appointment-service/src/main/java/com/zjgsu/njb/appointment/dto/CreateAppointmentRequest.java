package com.zjgsu.njb.appointment.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/**
 * 创建预约的请求对象。
 *
 * <p>{@code slotStart} 用 {@link Future} 约束：预约时间必须晚于当前时间。
 * petId / doctorId 是其他微服务的主键，本服务只保存 ID，不保存对方的任何数据副本。</p>
 */
public record CreateAppointmentRequest(

        @NotNull(message = "宠物 id 不能为空")
        Long petId,

        @NotNull(message = "医生 id 不能为空")
        Long doctorId,

        @NotNull(message = "预约开始时间不能为空")
        @Future(message = "预约开始时间必须晚于当前时间")
        LocalDateTime slotStart,

        @NotNull(message = "预约结束时间不能为空")
        LocalDateTime slotEnd,

        @Size(max = 255, message = "就诊原因不能超过 255 个字符")
        String reason
) {
}
