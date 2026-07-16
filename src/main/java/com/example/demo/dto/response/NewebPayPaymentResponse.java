package com.example.demo.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "NewebPay payment form data")
public class NewebPayPaymentResponse {

    @Schema(description = "Event application ID", example = "1")
    private Long applicationId;

    @Schema(description = "Event application number", example = "PAYTEST-APP-002")
    private String applicationNo;

    @Schema(description = "MarketDay payment record ID", example = "1")
    private Long paymentId;

    @Schema(description = "MarketDay payment number", example = "PAY202607060001")
    private String paymentNo;

    @Schema(description = "NewebPay MerchantOrderNo. Same as paymentNo.", example = "PAY202607060001")
    private String merchantOrderNo;

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

    public NewebPayPaymentResponse(
            String gateway,
            String merchantId,
            String tradeInfo,
            String tradeSha,
            String version,
            String paymentNo) {
        this(null, null, null, paymentNo, paymentNo, gateway, merchantId, tradeInfo, tradeSha, version);
    }

    public NewebPayPaymentResponse(
            Long applicationId,
            String applicationNo,
            Long paymentId,
            String paymentNo,
            String merchantOrderNo,
            String gateway,
            String merchantId,
            String tradeInfo,
            String tradeSha,
            String version) {
        this.applicationId = applicationId;
        this.applicationNo = applicationNo;
        this.paymentId = paymentId;
        this.paymentNo = paymentNo;
        this.merchantOrderNo = merchantOrderNo;
        this.gateway = gateway;
        this.merchantId = merchantId;
        this.tradeInfo = tradeInfo;
        this.tradeSha = tradeSha;
        this.version = version;
    }

    public Long getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(Long applicationId) {
        this.applicationId = applicationId;
    }

    public String getApplicationNo() {
        return applicationNo;
    }

    public void setApplicationNo(String applicationNo) {
        this.applicationNo = applicationNo;
    }

    public Long getPaymentId() {
        return paymentId;
    }

    public void setPaymentId(Long paymentId) {
        this.paymentId = paymentId;
    }

    public String getPaymentNo() {
        return paymentNo;
    }

    public void setPaymentNo(String paymentNo) {
        this.paymentNo = paymentNo;
    }

    public String getMerchantOrderNo() {
        return merchantOrderNo;
    }

    public void setMerchantOrderNo(String merchantOrderNo) {
        this.merchantOrderNo = merchantOrderNo;
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
}
