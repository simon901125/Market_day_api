package com.example.demo.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Vendor refund request result")
public class VendorRefundResponse {

    @Schema(description = "Refund record ID", example = "1")
    private Long refundId;

    @Schema(description = "Refund number", example = "REF20260717153012A1B2C")
    private String refundNo;

    @Schema(description = "Event application ID", example = "2")
    private Long applicationId;

    @Schema(description = "Event application number", example = "PAYTEST-APP-002")
    private String applicationNo;

    @Schema(description = "Payment record ID", example = "6")
    private Long paymentId;

    @Schema(description = "MarketDay payment number", example = "PAY20260716151711D40FD")
    private String paymentNo;

    @Schema(description = "NewebPay MerchantOrderNo. Same as paymentNo.", example = "PAY20260716151711D40FD")
    private String merchantOrderNo;

    @Schema(description = "NewebPay trade number", example = "2607161029099863")
    private String providerTradeNo;

    @Schema(description = "Refund amount excluding deposit", example = "1700")
    private BigDecimal refundAmount;

    @Schema(description = "Deposit amount not included in refund", example = "1000")
    private BigDecimal depositAmount;

    @Schema(description = "Refund method", example = "NEWEBPAY")
    private String refundMethod;

    @Schema(description = "Refund status", example = "REFUND_REQUESTED")
    private String refundStatus;

    @Schema(description = "Refund reason", example = "TEST")
    private String reason;

    @Schema(description = "Refund requested time", example = "2026-07-17T15:30:12")
    private LocalDateTime requestedAt;

    public VendorRefundResponse(
            Long refundId,
            String refundNo,
            Long applicationId,
            String applicationNo,
            Long paymentId,
            String paymentNo,
            String merchantOrderNo,
            String providerTradeNo,
            BigDecimal refundAmount,
            BigDecimal depositAmount,
            String refundMethod,
            String refundStatus,
            String reason,
            LocalDateTime requestedAt) {
        this.refundId = refundId;
        this.refundNo = refundNo;
        this.applicationId = applicationId;
        this.applicationNo = applicationNo;
        this.paymentId = paymentId;
        this.paymentNo = paymentNo;
        this.merchantOrderNo = merchantOrderNo;
        this.providerTradeNo = providerTradeNo;
        this.refundAmount = refundAmount;
        this.depositAmount = depositAmount;
        this.refundMethod = refundMethod;
        this.refundStatus = refundStatus;
        this.reason = reason;
        this.requestedAt = requestedAt;
    }

    public Long getRefundId() {
        return refundId;
    }

    public void setRefundId(Long refundId) {
        this.refundId = refundId;
    }

    public String getRefundNo() {
        return refundNo;
    }

    public void setRefundNo(String refundNo) {
        this.refundNo = refundNo;
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

    public String getProviderTradeNo() {
        return providerTradeNo;
    }

    public void setProviderTradeNo(String providerTradeNo) {
        this.providerTradeNo = providerTradeNo;
    }

    public BigDecimal getRefundAmount() {
        return refundAmount;
    }

    public void setRefundAmount(BigDecimal refundAmount) {
        this.refundAmount = refundAmount;
    }

    public BigDecimal getDepositAmount() {
        return depositAmount;
    }

    public void setDepositAmount(BigDecimal depositAmount) {
        this.depositAmount = depositAmount;
    }

    public String getRefundMethod() {
        return refundMethod;
    }

    public void setRefundMethod(String refundMethod) {
        this.refundMethod = refundMethod;
    }

    public String getRefundStatus() {
        return refundStatus;
    }

    public void setRefundStatus(String refundStatus) {
        this.refundStatus = refundStatus;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public LocalDateTime getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(LocalDateTime requestedAt) {
        this.requestedAt = requestedAt;
    }
}
