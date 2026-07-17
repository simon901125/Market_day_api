package com.example.demo.enums.type;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 通知中心分類，攤主、主辦方及管理員共用
 *
 * @see com.example.demo.entity.Notification
 */
@Getter
@RequiredArgsConstructor
public enum NotificationCategory {
    /**異常事件 */
    EXCEPTION("exception", "異常"),

    /**系統通知 */
    SYSTEM("system", "系統"),

    /**活動管理 */
    EVENT_MANAGEMENT("eventManagement", "活動管理"),

    /**主辦方管理 */
    ORGANIZER_MANAGEMENT("organizerManagement", "主辦方管理"),

    /**活動異動 */
    EVENT_CHANGE("eventChange", "活動異動"),

    /**攤位分配 */
    STALL_ASSIGNMENT("stallAssignment", "攤位分配"),

    /**款項 */
    PAYMENT("payment", "款項"),

    /**報名 */
    REGISTRATION("registration", "報名"),

    /**攤主申請審核 */
    APPLICATION_REVIEW("applicationReview", "攤主申請");

    @JsonValue
    private final String category; //序列化
    private final String description;

    @JsonCreator //反序列化
    public static NotificationCategory fromCategory(String category){
        for (NotificationCategory c : values()) {
            if (c.category.equalsIgnoreCase(category) || c.description.equals(category) || c.name().equals(category)) {
                return c;
            }
        }
        return null;
    }
}
