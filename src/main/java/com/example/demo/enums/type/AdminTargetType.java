package com.example.demo.enums.type;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 
 * 管理員操作對象類型，包含系統、活動下架申請、活動、使用者
 * 
 * @see com.example.demo.entity.AdminOperationLog
 */
@Getter
@RequiredArgsConstructor
public enum AdminTargetType {
    /**系統 */
    SYSTEM_SETTING("systemSetting", "系統"), 

    /**活動下架申請 */
    EVENT_UNPUBLISH_REQUEST("eventUnpublishRequest", "活動下架申請"), 

    /**活動 */
    MARKET_EVENT("marketEvent", "活動"), 
    
    /**使用者 */
    USER("user", "使用者");

    @JsonValue
    private final String type; //序列化
    private final String description;

    @JsonCreator //反序列化
    public static AdminTargetType fromType(String type){
        for (AdminTargetType t : values()) {
            if (t.type.equalsIgnoreCase(type) || t.description.equals(type) || t.name().equals(type)) {
                return t;
            }
        }
        return null;
    }
}
