package com.zjgsu.njb.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 创建宠物档案的请求对象。
 *
 * <p>使用 DTO 而不是直接接收实体，可以精确控制哪些字段允许客户端提交
 * （例如 {@code id}、{@code createdAt} 由服务端生成，不接受外部输入）。</p>
 */
public record CreatePetRequest(

        @NotBlank(message = "宠物昵称不能为空")
        @Size(max = 50, message = "宠物昵称不能超过 50 个字符")
        String name,

        @NotBlank(message = "物种不能为空")
        @Size(max = 20, message = "物种不能超过 20 个字符")
        String species,

        @Size(max = 50, message = "品种不能超过 50 个字符")
        String breed,

        @NotBlank(message = "主人姓名不能为空")
        @Size(max = 50, message = "主人姓名不能超过 50 个字符")
        String ownerName,

        @Size(max = 20, message = "联系电话不能超过 20 个字符")
        String ownerPhone,

        @PastOrPresent(message = "出生日期不能晚于今天")
        LocalDate birthday,

        @DecimalMin(value = "0.0", message = "体重不能为负数")
        @Digits(integer = 3, fraction = 2, message = "体重整数位最多 3 位、小数位最多 2 位")
        BigDecimal weightKg,

        @Size(max = 255, message = "过敏史不能超过 255 个字符")
        String allergyHistory
) {
}
