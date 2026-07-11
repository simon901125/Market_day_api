package com.example.demo.dto.request.admin;

import java.time.LocalDateTime;

import com.example.demo.enums.EventStatus;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 管理員: 活動搜尋頁面 API 請求(含分頁參數)
 *
 * @param keyword 活動/主辦方名稱 :String
 * @param organizer 活動主辦方 :String
 * @param status 活動狀態{@link EventStatus}
 * @param startDate 活動開始時間 :LocalDateTime
 * @param endDate 活動結束時間 :LocalDateTime
 * @param pageNumber 頁碼，從1開始計算 :int
 * @param pageSize 每頁筆數 :int
 */
@Schema(description = "管理員: 活動搜尋頁面 API 請求")
public record AdminEventSearchRequest(
    String keyword,
    String organizer,
    EventStatus status,
    LocalDateTime startDate,
    LocalDateTime endDate,
    int pageNumber,
    int pageSize
) {}
