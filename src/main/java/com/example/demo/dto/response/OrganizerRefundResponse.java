package com.example.demo.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Organizer refund review result")
public class OrganizerRefundResponse {

    @Schema(description = "Refund record ID", example = "2")
    private Long refundId;

    @Schema(description = "Refund number", example = "REF2026071715210188A95")
    private String refundNo;

    @Schema(description = "Refund amount", example = "700")
    private BigDecimal refundAmount;

    @Schema(description = "Refund status", example = "REFUNDED")
    private String refundStatus;

    @Schema(description = "Refund completed time", example = "2026-07-18T15:30:12")
    private LocalDateTime refundedAt;

    @Schema(description = "Event application ID", example = "2")
    private Long applicationId;

    @Schema(description = "Event application number", example = "PAYTEST-APP-002")
    private String applicationNo;

    @Schema(description = "Payment record ID", example = "9")
    private Long paymentId;

    @Schema(description = "MarketDay payment number", example = "PAY20260717145426CF234")
    private String paymentNo;

    @Schema(description = "NewebPay MerchantOrderNo. Same as paymentNo.", example = "PAY20260717145426CF234")
    private String merchantOrderNo;

    @Schema(description = "NewebPay trade number", example = "26071714554003655")
    private String providerTradeNo;

    @Schema(description = "NewebPay close result")
    private NewebPayRefundResultResponse newebpayResult;

    public OrganizerRefundResponse() {
    }

    public OrganizerRefundResponse(
            Long refundId,
            String refundNo,
            BigDecimal refundAmount,
            String refundStatus,
            LocalDateTime refundedAt,
            Long applicationId,
            String applicationNo,
            Long paymentId,
            String paymentNo,
            String merchantOrderNo,
            String providerTradeNo,
            NewebPayRefundResultResponse newebpayResult) {
        this.refundId = refundId;
        this.refundNo = refundNo;
        this.refundAmount = refundAmount;
        this.refundStatus = refundStatus;
        this.refundedAt = refundedAt;
        this.applicationId = applicationId;
        this.applicationNo = applicationNo;
        this.paymentId = paymentId;
        this.paymentNo = paymentNo;
        this.merchantOrderNo = merchantOrderNo;
        this.providerTradeNo = providerTradeNo;
        this.newebpayResult = newebpayResult;
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

    public BigDecimal getRefundAmount() {
        return refundAmount;
    }

    public void setRefundAmount(BigDecimal refundAmount) {
        this.refundAmount = refundAmount;
    }

    public String getRefundStatus() {
        return refundStatus;
    }

    public void setRefundStatus(String refundStatus) {
        this.refundStatus = refundStatus;
    }

    public LocalDateTime getRefundedAt() {
        return refundedAt;
    }

    public void setRefundedAt(LocalDateTime refundedAt) {
        this.refundedAt = refundedAt;
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

    public NewebPayRefundResultResponse getNewebpayResult() {
        return newebpayResult;
    }

    public void setNewebpayResult(NewebPayRefundResultResponse newebpayResult) {
        this.newebpayResult = newebpayResult;
    }
}