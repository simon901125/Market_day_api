package com.example.demo.enums.notification;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 通知關聯對象類型，SYSTEM 類型無對應資料（target_id 為 NULL）
 *
 * @see com.example.demo.entity.Notification
 */
@Getter
@RequiredArgsConstructor
public enum NotificationTargetType {
    /**系統 */
    SYSTEM("system", "系統"),

    /**退款 */
    REFUND("refund", "退款"),

    /**款項 */
    PAYMENT("payment", "款項"),

    /**主辦方資料 */
    ORGANIZER_PROFILE("organizerProfile", "主辦方資料"),

    /**使用者 */
    USER("user", "使用者"),

    /**活動 */
    MARKET_EVENT("marketEvent", "活動"),

    /**攤位申請 */
    EVENT_APPLICATION("eventApplication", "攤位申請"),

    /**活動下架申請單 */
    EVENT_UNPUBLISH_REQUEST("eventUnpublishRequest", "活動下架申請單");

    @JsonValue
    private final String type; //序列化
    private final String description;

    @JsonCreator //反序列化
    public static NotificationTargetType fromType(String type){
        for (NotificationTargetType t : values()) {
            if (t.type.equalsIgnoreCase(type) || t.description.equals(type) || t.name().equals(type)) {
                return t;
            }
        }
        return null;
    }
}
