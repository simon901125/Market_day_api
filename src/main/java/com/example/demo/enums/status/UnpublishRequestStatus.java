package com.example.demo.enums.status;

import com.example.demo.entity.EventUnpublishRequest;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 活動下架申請的審核狀態，包含待審核、審核通過、審核拒絕、已取消
 *
 * @see EventUnpublishRequest#status
 */
@Getter
@RequiredArgsConstructor
public enum UnpublishRequestStatus {

    /**待審核 */
    PENDING("pending", "待審核"),
    /**審核通過 */
    APPROVED("approved", "審核通過"),
    /**審核拒絕 */
    REJECTED("rejected", "審核拒絕"),
    /**已取消 */
    CANCELLED("cancelled", "已取消");

    @JsonValue
    private final String status;
    private final String description;

    @JsonCreator
    public static UnpublishRequestStatus fromStatus(String status) {
        for (UnpublishRequestStatus s : values()) {
            if (s.status.equalsIgnoreCase(status) || s.description.equals(status) || s.name().equals(status)) {
                return s;
            }
        }
        return null;
    }
}
