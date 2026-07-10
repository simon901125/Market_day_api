package com.example.demo.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "NewebPay payment form data")
public class NewebPayPaymentResponse {

    @Schema(description = "NewebPay payment gateway URL", example = "https://ccore.newebpay.com/MPG/mpg_gateway")
    private String gateway;

    @Schema(description = "NewebPay merchant ID", example = "MS123456789")
    private String merchantId;

    @Schema(description = "Encrypted payment trade information")
    private String tradeInfo;

    @Schema(description = "SHA256 verification value for TradeInfo")
    private String tradeSha;

    @Schema(description = "NewebPay API version", example = "2.3")
    private String version;

    @Schema(description = "MarketDay payment number", example = "PAY202607060001")
    private String paymentNo;

    public NewebPayPaymentResponse(
            String gateway,
            String merchantId,
            String tradeInfo,
            String tradeSha,
            String version,
            String paymentNo) {
        this.gateway = gateway;
        this.merchantId = merchantId;
        this.tradeInfo = tradeInfo;
        this.tradeSha = tradeSha;
        this.version = version;
        this.paymentNo = paymentNo;
    }

    public String getGateway() {
        return gateway;
    }

    public void setGateway(String gateway) {
        this.gateway = gateway;
    }

    public String getMerchantId() {
        return merchantId;
    }

    public void setMerchantId(String merchantId) {
        this.merchantId = merchantId;
    }

    public String getTradeInfo() {
        return tradeInfo;
    }

    public void setTradeInfo(String tradeInfo) {
        this.tradeInfo = tradeInfo;
    }

    public String getTradeSha() {
        return tradeSha;
    }

    public void setTradeSha(String tradeSha) {
        this.tradeSha = tradeSha;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getPaymentNo() {
        return paymentNo;
    }

    public void setPaymentNo(String paymentNo) {
        this.paymentNo = paymentNo;
    }
}
