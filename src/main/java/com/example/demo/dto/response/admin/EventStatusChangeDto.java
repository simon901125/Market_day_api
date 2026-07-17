package com.example.demo.dto.response.admin;

import com.example.demo.enums.status.EventStatus;

/**
 * 
 * EventStatusChangeDto
 * @param eventName 活動名稱
 * @param newEventStatus 新的活動狀態
 */
public record EventStatusChangeDto(
    String eventName, 
    EventStatus newEventStatus
) {}
