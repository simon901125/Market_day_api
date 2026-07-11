package com.example.demo.dto.request.admin;

import java.time.LocalDateTime;

import com.example.demo.enums.EventStatus;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 管理員: 活動搜尋頁面搜尋條件
 * @param keywordName 活動/主辦方名稱(模糊搜尋) :String
 * @param organizer 活動主辦方 :String
 * @param status 活動狀態{@link EventStatus}
 * @param startAt 活動開始時間 :LocalDateTime
 * @param endAt 活動結束時間 :LocalDateTime
 */
@Schema(description = "管理員: 活動搜尋頁面搜尋條件")
public record AdminEventSearchDto(
    String keywordName, 
    String organizer,
    EventStatus status,
    LocalDateTime startAt,
    LocalDateTime endAt
) {}
