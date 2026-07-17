package com.example.demo.dto.response;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "主辦方通知中心單筆通知")
public record OrganizerNotificationItemResponse(
        @Schema(description = "通知 ID") Long id,
        @Schema(description = "通知分類") String category,
        @Schema(description = "通知事件類型") String type,
        @Schema(description = "關聯資料類型") String targetType,
        @Schema(description = "關聯資料 ID；系統通知可為 null") Long targetId,
        @Schema(description = "通知標題") String title,
        @Schema(description = "通知內容") String content,
        @Schema(description = "是否已讀") boolean isRead,
        @Schema(description = "閱讀時間") LocalDateTime readAt,
        @Schema(description = "通知建立時間") LocalDateTime createdAt) {
}
