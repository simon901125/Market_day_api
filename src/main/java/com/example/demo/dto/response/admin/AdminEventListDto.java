package com.example.demo.dto.response.admin;

import com.example.demo.enums.status.EventStatus;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 管理員: 活動列表
 * @param id 活動id
 * @param imgUrl 活動圖片url
 * @param event 活動名稱
 * @param date 活動日期 yyyy/MM/dd - yyyy/MM/dd
 * @param status 活動當前狀態
 * @param organizer 主辦方名稱
 * @param reviewTime 活動送審時間
 */
@Schema(description = "管理員活動列表")
public record AdminEventListDto(
        Long id,
        String imgUrl,
        String event,
        String date,
        EventStatus status,
        String organizer,
        String reviewTime
        
) {}

