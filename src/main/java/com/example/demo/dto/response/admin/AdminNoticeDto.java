package com.example.demo.dto.response.admin;

import com.example.demo.enums.notification.NotificationTargetType;
import com.example.demo.enums.notification.NotificationType;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 管理員: 通知中心列表
 * @param id 通知id
 * @param type 通知事件類型，前端用來決定icon
 * @param targetType 通知關聯對象類型，前端用來決定跳轉
 * @param targetId 通知關聯資料id，前端用來決定跳轉
 * @param title 通知標題
 * @param content 通知內容
 * @param isRead 是否已讀
 * @param time 通知建立時間 yyyy/MM/dd HH:mm
 */
@Schema(description = "管理員通知中心列表")
public record AdminNoticeDto(
        Long id,
        NotificationType type,
        NotificationTargetType targetType,
        Long targetId,
        String title,
        String content,
        boolean isRead,
        String time
) {}
