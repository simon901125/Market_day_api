package com.example.demo.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**用來對應{@link com.example.demo.entity.EventApplication}中的報名審核狀態 */
@Getter
@RequiredArgsConstructor
public enum ReviewStatus {

    /**待審核 */
    PENDING("pending", "待審核"),
    /**審核通過 */
    APPROVED("approved", "審核通過"),
    /**審核拒絕 */
    REJECTED("rejected", "審核拒絕");

    @JsonValue
    private final String status;
    private final String description;

    @JsonCreator
    public static ReviewStatus fromStatus(String status) {
        for (ReviewStatus s : values()) {
            if (s.status.equals(status)) {
                return s;
            }
        }
        throw new IllegalArgumentException("未知的狀態: " + status);
    }
}
