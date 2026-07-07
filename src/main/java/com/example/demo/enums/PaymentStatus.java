package com.example.demo.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**用來對應{@link com.example.demo.entity.EventApplication}中的付款狀態 */
@Getter
@RequiredArgsConstructor
public enum PaymentStatus {

    /**待付款 */
    PENDING("pending", "待付款"),
    /**付款成功 */
    PAID("paid", "付款成功"),
    /**付款失敗 */
    FAILED("failed", "付款失敗"),
    /**已逾期 */
    EXPIRED("expired", "已逾期");

    @JsonValue
    private final String status;
    private final String description;

    @JsonCreator
    public static PaymentStatus fromStatus(String status) {
        for (PaymentStatus s : values()) {
            if (s.status.equals(status)) {
                return s;
            }
        }
        throw new IllegalArgumentException("未知的狀態: " + status);
    }
}
