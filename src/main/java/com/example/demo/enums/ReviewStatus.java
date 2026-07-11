package com.example.demo.enums;

import com.example.demo.entity.EventApplication;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 攤主報名活動的審核狀態，包含待審核、審核通過、審核拒絕
 * 
 * @see EventApplication#reviewStatus
 */
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
        return null;
    }
}
