package com.example.demo.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**用來對應{@link com.example.demo.entity.EventApplication}中的保證金狀態 */
@Getter
@RequiredArgsConstructor
public enum DepositStatus {

    /**保證金未退還 */
    NOT_RETURNED("notReturned", "未退還"),
    /**保證金已退還 */
    RETURNED("returned", "已退還");

    @JsonValue
    private final String status;
    private final String description;

    @JsonCreator
    public static DepositStatus fromStatus(String status) {
        for (DepositStatus s : values()) {
            if (s.status.equals(status)) {
                return s;
            }
        }
        throw new IllegalArgumentException("未知的狀態: " + status);
    }
}
