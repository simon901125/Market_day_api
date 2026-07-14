package com.example.demo.enums.status;

import com.example.demo.entity.Refund;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 退款的處理狀態，包含已申請退款、退款中、已退款、退款失敗
 *
 * @see Refund#refundStatus
 */
@Getter
@RequiredArgsConstructor
public enum RefundStatus {

    /**已申請退款 */
    REFUND_REQUESTED("refundRequested", "已申請退款"),
    /**退款中 */
    REFUNDING("refunding", "退款中"),
    /**已退款 */
    REFUNDED("refunded", "已退款"),
    /**退款失敗 */
    REFUND_FAILED("refundFailed", "退款失敗");

    @JsonValue
    private final String status;
    private final String description;

    @JsonCreator
    public static RefundStatus fromStatus(String status) {
        for (RefundStatus s : values()) {
            if (s.status.equalsIgnoreCase(status) || s.description.equals(status) || s.name().equals(status)) {
                return s;
            }
        }
        return null;
    }
}
