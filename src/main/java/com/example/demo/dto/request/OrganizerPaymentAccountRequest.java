package com.example.demo.dto.request;

import jakarta.validation.constraints.NotBlank;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "主辦方藍新商店設定")
public record OrganizerPaymentAccountRequest(
        @Schema(description = "藍新商店代號", example = "MS123456789", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "MerchantID 不可為空") String merchantId,
        @Schema(description = "藍新商店 HashKey，固定 32 個字元", example = "12345678901234567890123456789012", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "HashKey 不可為空") String hashKey,
        @Schema(description = "藍新商店 HashIV，固定 16 個字元", example = "1234567890123456", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "HashIV 不可為空") String hashIv) {
}
