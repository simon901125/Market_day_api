package com.example.demo.enums.status;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 
 * 活動攤位狀態，包含未開放選位、可選、已被選擇、自動分配
 * 
 * @see com.example.demo.entity.EventStall
 */
@Getter
@RequiredArgsConstructor
public enum StallStatus {
    /**可選 */
    AVAILABLE("available", "可選"), 
    /**未開放選位 */
    DISABLED("disable", "未開放選位"), 
    /**自動分配 */
    ASSIGNED("assigned", "自動分配"), 
    /**已被選擇 */
    SELECTED("selected", "已被選擇");

    @JsonValue
    private final String status;
    private final String description;

    @JsonCreator
    public StallStatus fromStatus(String status){
        for (StallStatus s : values()) {
            if (s.status.equalsIgnoreCase(status) || s.description.equals(status) || s.name().equals(status)) {
                return s;
            }
        }
        return null;
    }
}
