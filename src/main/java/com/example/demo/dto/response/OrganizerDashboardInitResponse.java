package com.example.demo.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "主辦方後台首次進入時的設定完成狀態")
public record OrganizerDashboardInitResponse(
        @Schema(description = "是否仍需完成主辦方基本資料")
        boolean needsProfile,
        @Schema(description = "基本資料完成後，是否仍需綁定並驗證藍新金流帳戶")
        boolean needsPaymentAccount,
        @Schema(description = "是否已符合建立活動的全部前置條件")
        boolean canCreateEvent,
        @Schema(description = "藍新帳戶狀態；尚未綁定時為 null", example = "ACTIVE")
        String paymentAccountStatus,
        @Schema(description = "藍新驗證狀態；尚未綁定時為 UNVERIFIED", example = "VERIFIED")
        String paymentAccountVerificationStatus) {
}
