package com.zjgsu.njb.appointment.remote;

/**
 * pet-service 返回的宠物视图（本服务对远端数据的只读快照模型）。
 */
public record PetView(
        Long id,
        String name,
        String species,
        String ownerName
) {
}
