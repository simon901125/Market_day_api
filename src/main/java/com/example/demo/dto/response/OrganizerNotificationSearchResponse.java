package com.example.demo.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "主辦方通知中心查詢結果")
public record OrganizerNotificationSearchResponse(
        @Schema(description = "目前主辦方在通知保留期內的未讀通知總數") long unreadCount,
        @Schema(description = "通知分頁資料") PageResponse<OrganizerNotificationItemResponse> notifications) {
}
