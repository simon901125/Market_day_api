package com.example.demo.dto.response;

import java.time.LocalDateTime;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "主辦方藍新商店綁定摘要")
public record OrganizerPaymentAccountResponse(
        @Schema(description = "遮罩後的 MerchantID", example = "MS123***789")
        String merchantId,
        @Schema(description = "帳戶設定狀態", example = "ACTIVE")
        String status,
        @Schema(description = "最後更新時間")
        LocalDateTime updatedAt) {
}
