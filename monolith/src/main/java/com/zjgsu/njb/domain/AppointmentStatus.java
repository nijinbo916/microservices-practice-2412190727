package com.zjgsu.njb.domain;

/**
 * 预约状态机。
 *
 * <p>BOOKED（已预约）→ CHECKED_IN（已到店登记）→ COMPLETED（就诊完成）；
 * 在前两步都可以转为 CANCELED（已取消）。</p>
 */
public enum AppointmentStatus {

    /** 已预约，等待就诊。 */
    BOOKED,

    /** 已到店登记，等待医生接诊。 */
    CHECKED_IN,

    /** 就诊完成。 */
    COMPLETED,

    /** 已取消。 */
    CANCELED;

    /** 该状态是否占用号源（占用号源的状态不能与其他预约冲突）。 */
    public boolean occupiesSlot() {
        return this == BOOKED || this == CHECKED_IN;
    }
}
