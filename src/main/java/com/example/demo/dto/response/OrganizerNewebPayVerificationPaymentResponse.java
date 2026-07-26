package com.example.demo.dto.response;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "藍新商店 NT$1 綁定驗證付款資料")
public record OrganizerNewebPayVerificationPaymentResponse(
        @Schema(description = "Market Day 驗證訂單編號", example = "NPV20260726123000A1B2")
        String verificationNo,
        @Schema(description = "驗證金額", example = "1")
        BigDecimal amount,
        @Schema(description = "藍新付款頁網址")
        String gateway,
        @Schema(description = "藍新 MerchantID", example = "MS123456789")
        String merchantId,
        @Schema(description = "加密交易資料")
        String tradeInfo,
        @Schema(description = "TradeInfo 的 SHA256 驗證值")
        String tradeSha,
        @Schema(description = "藍新 MPG 版本", example = "2.3")
        String version) {
}
