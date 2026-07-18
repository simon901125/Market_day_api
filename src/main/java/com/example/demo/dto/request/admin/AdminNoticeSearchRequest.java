package com.example.demo.dto.request.admin;

import com.example.demo.enums.notification.NotificationCategory;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 管理員: 通知中心列表 API 請求(含分頁參數)
 *
 * @param isOnlyUnread 為true時查詢全部分類 :NotificationCategory, isRead = false，為false/null時不做isRead = false的判斷
 * @param category 通知分類{@link NotificationCategory}，為 null 時查詢全部分類 :NotificationCategory
 * @param pageNumber 頁碼，從1開始計算，為 null 時預設1 :Integer
 * @param pageSize 每頁筆數，為 null 時使用預設頁面大小 :Integer
 */
@Schema(description = "管理員: 通知中心列表 API 請求")
public record AdminNoticeSearchRequest(
    Boolean isOnlyUnread,
    NotificationCategory category,
    Integer pageNumber,
    Integer pageSize
) {}
