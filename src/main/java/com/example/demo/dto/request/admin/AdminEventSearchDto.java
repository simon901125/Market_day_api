package com.example.demo.dto.request.admin;

import java.time.LocalDateTime;

import com.example.demo.enums.EventStatus;

import io.swagger.v3.oas.annotations.media.Schema;

/**管理員: 活動搜尋頁面搜尋請求 */
@Schema(description = "管理員: 活動搜尋頁面搜尋請求")
public record AdminEventSearchDto(
    //活動名稱
    String name, 
    //活動主辦方
    String Organizer,
    //活動狀態
    EventStatus status,
    //活動開始時間
    LocalDateTime startAt,
    //活動結束時間
    LocalDateTime endAt
) {}
