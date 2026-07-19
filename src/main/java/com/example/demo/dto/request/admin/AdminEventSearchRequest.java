package com.example.demo.dto.request.admin;

import java.time.LocalDateTime;

import com.example.demo.enums.status.EventStatus;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 管理員: 活動搜尋頁面 API 請求(含分頁參數)
 *
 * @param keyword 活動/主辦方名稱 :String
 * @param organizer 活動主辦方 :String
 * @param status 活動狀態{@link EventStatus}
 * @param startDate 活動開始時間 :LocalDateTime
 * @param endDate 活動結束時間 :LocalDateTime
 * @param pageNumber 頁碼，從1開始計算，為 null 時預設1 :Integer
 * @param pageSize 每頁筆數，為 null 時使用預設頁面大小 :Integer
 */
@Schema(description = "管理員: 活動搜尋頁面 API 請求")
public record AdminEventSearchRequest(
    String keyword,
    String organizer,
    EventStatus status,
    LocalDateTime startDate,
    LocalDateTime endDate,
    Integer pageNumber,
    Integer pageSize
) {}
