package com.example.demo.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Payment status response")
public class PaymentStatusResponse {

    private String applicationNo;
    private String reviewStatus;
    private String applicationPaymentStatus;
    private Boolean cancelled;
    private BigDecimal applicationAmount;
    private LocalDateTime paymentDueAt;
    private String paymentNo;
    private BigDecimal paymentAmount;
    private String provider;
    private String providerTradeNo;
    private String paymentRecordStatus;
    private LocalDateTime paidAt;
    private LocalDateTime paymentCreatedAt;

    public String getApplicationNo() {
        return applicationNo;
    }

    public void setApplicationNo(String applicationNo) {
        this.applicationNo = applicationNo;
    }

    public String getReviewStatus() {
        return reviewStatus;
    }

    public void setReviewStatus(String reviewStatus) {
        this.reviewStatus = reviewStatus;
    }

    public String getApplicationPaymentStatus() {
        return applicationPaymentStatus;
    }

    public void setApplicationPaymentStatus(String applicationPaymentStatus) {
        this.applicationPaymentStatus = applicationPaymentStatus;
    }

    public Boolean getCancelled() {
        return cancelled;
    }

    public void setCancelled(Boolean cancelled) {
        this.cancelled = cancelled;
    }

    public BigDecimal getApplicationAmount() {
        return applicationAmount;
    }

    public void setApplicationAmount(BigDecimal applicationAmount) {
        this.applicationAmount = applicationAmount;
    }

    public LocalDateTime getPaymentDueAt() {
        return paymentDueAt;
    }

    public void setPaymentDueAt(LocalDateTime paymentDueAt) {
        this.paymentDueAt = paymentDueAt;
    }

    public String getPaymentNo() {
        return paymentNo;
    }

    public void setPaymentNo(String paymentNo) {
        this.paymentNo = paymentNo;
    }

    public BigDecimal getPaymentAmount() {
        return paymentAmount;
    }

    public void setPaymentAmount(BigDecimal paymentAmount) {
        this.paymentAmount = paymentAmount;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getProviderTradeNo() {
        return providerTradeNo;
    }

    public void setProviderTradeNo(String providerTradeNo) {
        this.providerTradeNo = providerTradeNo;
    }

    public String getPaymentRecordStatus() {
        return paymentRecordStatus;
    }

    public void setPaymentRecordStatus(String paymentRecordStatus) {
        this.paymentRecordStatus = paymentRecordStatus;
    }

    public LocalDateTime getPaidAt() {
        return paidAt;
    }

    public void setPaidAt(LocalDateTime paidAt) {
        this.paidAt = paidAt;
    }

    public LocalDateTime getPaymentCreatedAt() {
        return paymentCreatedAt;
    }

    public void setPaymentCreatedAt(LocalDateTime paymentCreatedAt) {
        this.paymentCreatedAt = paymentCreatedAt;
    }
}
