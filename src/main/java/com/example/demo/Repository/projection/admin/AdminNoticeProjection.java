package com.example.demo.Repository.projection.admin;

import java.time.LocalDateTime;

import com.example.demo.enums.notification.NotificationTargetType;
import com.example.demo.enums.notification.NotificationType;

/**
 * 管理員:通知中心列表查詢欄位
 * @param id 通知id
 * @param type 通知事件類型(前端用來決定icon)
 * @param targetType 通知關聯對象類型(前端用來決定跳轉)
 * @param targetId 通知關聯資料id(前端用來決定跳轉)
 * @param title 通知標題
 * @param content 通知內容
 * @param isRead 是否已讀
 * @param createdAt 通知建立時間
 *
 * @see com.example.demo.Repository.NotificationRepo#findAdminNotices(Long, com.example.demo.enums.notification.NotificationCategory, org.springframework.data.domain.Pageable)
 */
public record AdminNoticeProjection(
    Long id,
    NotificationType type,
    NotificationTargetType targetType,
    Long targetId,
    String title,
    String content,
    boolean isRead,
    LocalDateTime createdAt
) {}
