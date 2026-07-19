package com.example.demo.dto.response;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "NewebPay credit card close result")
public class NewebPayRefundResultResponse {

    @Schema(description = "Merchant ID", example = "MS159696944")
    private String merchantID;

    @Schema(description = "Refund amount", example = "700")
    private BigDecimal amt;

    @Schema(description = "NewebPay trade number", example = "26071714554003655")
    private String tradeNo;

    @Schema(description = "Merchant order number", example = "PAY20260717145426CF234")
    private String merchantOrderNo;

    public NewebPayRefundResultResponse() {
    }

    public NewebPayRefundResultResponse(String merchantID, BigDecimal amt, String tradeNo, String merchantOrderNo) {
        this.merchantID = merchantID;
        this.amt = amt;
        this.tradeNo = tradeNo;
        this.merchantOrderNo = merchantOrderNo;
    }

    public String getMerchantID() {
        return merchantID;
    }

    public void setMerchantID(String merchantID) {
        this.merchantID = merchantID;
    }

    public BigDecimal getAmt() {
        return amt;
    }

    public void setAmt(BigDecimal amt) {
        this.amt = amt;
    }

    public String getTradeNo() {
        return tradeNo;
    }

    public void setTradeNo(String tradeNo) {
        this.tradeNo = tradeNo;
    }

    public String getMerchantOrderNo() {
        return merchantOrderNo;
    }

    public void setMerchantOrderNo(String merchantOrderNo) {
        this.merchantOrderNo = merchantOrderNo;
    }
}