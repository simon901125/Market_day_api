package com.example.demo.enums.type;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AdminOperationType {
    SYSTEM_SETTING("systemSetting", "系統設定"), 
    MAP_BUILD_COMPLETED("mapBuildCompleted", "完成地圖建置"), 
    EVENT_UNPUBLISH_REVIEW("eventUnpublishReview", "活動下架審核"), 
    ACCOUNT_RESTORED("accountRestored", "帳號恢復"), 
    ACCOUNT_DISABLED("accountDisabled", "帳號停用"), 
    REQUEST_REVISION("requestRevision", "要求補件"), 
    ACTIVITY_REVIEW("activityReview", "活動審核");

    @JsonValue
    private final String type;
    private final String description;

    @JsonCreator
    public static AdminOperationType fromType(String type){
        for (AdminOperationType t : values()) {
            if (t.type.equalsIgnoreCase(type) || t.description.equals(type) || t.name().equals(type)) {
                return t;
            }
        }
        return null;
    }
}
