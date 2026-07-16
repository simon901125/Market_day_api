package com.example.demo.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 攤主登入後的後台初始化狀態。
 *
 * @param needsProfileSetup 是否需要先填寫攤位資料
 */
@Schema(description = "攤主後台初始化結果")
public record VendorDashboardInitResponse(
        @Schema(description = "是否需要填寫攤位資料", example = "true")
        boolean needsProfileSetup) {
}
