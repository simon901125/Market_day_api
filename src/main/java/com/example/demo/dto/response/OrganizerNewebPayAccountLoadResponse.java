package com.example.demo.dto.response;

import java.time.LocalDateTime;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "主辦方藍新商店設定載入結果")
public record OrganizerNewebPayAccountLoadResponse(
        @Schema(description = "是否已綁定藍新商店", example = "true")
        boolean bound,
        @Schema(description = "藍新商店代號；尚未綁定時為 null", example = "MS123456789")
        String merchantId,
        @Schema(description = "基於安全考量固定回傳空字串", example = "")
        String hashKey,
        @Schema(description = "基於安全考量固定回傳空字串", example = "")
        String hashIv,
        @Schema(description = "帳戶設定狀態", example = "ACTIVE")
        String status,
        @Schema(description = "小額付款驗證狀態：UNVERIFIED、PENDING、VERIFIED 或 FAILED", example = "VERIFIED")
        String verificationStatus,
        @Schema(description = "小額付款驗證完成時間")
        LocalDateTime verifiedAt,
        @Schema(description = "最後更新時間")
        LocalDateTime updatedAt) {
}
