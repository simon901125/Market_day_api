package com.example.demo.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.demo.Repository.PaymentRepository;
import com.example.demo.dto.request.VendorRefundRequest;
import com.example.demo.dto.response.ApiResponse;
import com.example.demo.dto.response.VendorRefundResponse;

@ExtendWith(MockitoExtension.class)
class VendorRefundServiceTest {

    @Mock PaymentRepository paymentRepository;
    @Mock JwtService jwtService;
    @Mock NotificationService notificationService;

    VendorRefundService service;

    @BeforeEach
    void setUp() {
        service = new VendorRefundService();
        ReflectionTestUtils.setField(service, "paymentRepository", paymentRepository);
        ReflectionTestUtils.setField(service, "jwtService", jwtService);
        ReflectionTestUtils.setField(service, "notificationService", notificationService);
    }

    @Test
    void requestRefundRequiresApplicationNumberAndReasonBeforeAuthentication() {
        assertThat(service.requestRefund(null, null).isSuccessStatus()).isFalse();

        VendorRefundRequest request = new VendorRefundRequest();
        request.setApplicationNo("PAYTEST-APP-002");
        request.setReason(" ");

        assertThat(service.requestRefund(null, request).isSuccessStatus()).isFalse();
    }

    @Test
    void requestRefundRejectsNonVendorToken() {
        VendorRefundRequest request = request();
        when(jwtService.extractTokenFromAuthorizationHeader("Bearer token")).thenReturn("token");
        when(jwtService.isTokenValid("token")).thenReturn(true);
        when(jwtService.getRole("token")).thenReturn("ORGANIZER");

        ApiResponse<VendorRefundResponse> response = service.requestRefund("Bearer token", request);

        assertThat(response.isSuccessStatus()).isFalse();
        assertThat(response.getStatusCode()).isEqualTo(403);
    }

    @Test
    void requestRefundRejectsApplicationOwnedByAnotherVendor() {
        VendorRefundRequest request = request();
        mockVendorToken();
        when(paymentRepository.findVendorPaymentUserByEmail("vendor@test.local"))
                .thenReturn(Optional.of(Map.of("userId", 1L)));
        when(paymentRepository.findRefundableApplication("PAYTEST-APP-002"))
                .thenReturn(Optional.of(refundableApplication(2L, "PAID")));

        ApiResponse<VendorRefundResponse> response = service.requestRefund("Bearer token", request);

        assertThat(response.isSuccessStatus()).isFalse();
        assertThat(response.getMessage()).contains("不屬於");
    }

    @Test
    void requestRefundRejectsUnpaidApplication() {
        VendorRefundRequest request = request();
        mockVendorToken();
        when(paymentRepository.findVendorPaymentUserByEmail("vendor@test.local"))
                .thenReturn(Optional.of(Map.of("userId", 1L)));
        when(paymentRepository.findRefundableApplication("PAYTEST-APP-002"))
                .thenReturn(Optional.of(refundableApplication(1L, "PENDING")));

        ApiResponse<VendorRefundResponse> response = service.requestRefund("Bearer token", request);

        assertThat(response.isSuccessStatus()).isFalse();
        verify(paymentRepository, never()).createRefund(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void requestRefundRejectsDuplicateRefund() {
        VendorRefundRequest request = request();
        mockVendorToken();
        when(paymentRepository.findVendorPaymentUserByEmail("vendor@test.local"))
                .thenReturn(Optional.of(Map.of("userId", 1L)));
        when(paymentRepository.findRefundableApplication("PAYTEST-APP-002"))
                .thenReturn(Optional.of(refundableApplication(1L, "PAID")));
        when(paymentRepository.findLatestPaidPayment(2L)).thenReturn(Optional.of(paidPayment()));
        when(paymentRepository.findLatestRefundByApplicationId(2L))
                .thenReturn(Optional.of(Map.of("refundId", 9L)));

        ApiResponse<VendorRefundResponse> response = service.requestRefund("Bearer token", request);

        assertThat(response.isSuccessStatus()).isFalse();
        assertThat(response.getMessage()).contains("已提出退款申請");
    }

    @Test
    void requestRefundCreatesRefundExcludingDeposit() {
        VendorRefundRequest request = request();
        mockVendorToken();
        when(paymentRepository.findVendorPaymentUserByEmail("vendor@test.local"))
                .thenReturn(Optional.of(Map.of("userId", 1L)));
        when(paymentRepository.findRefundableApplication("PAYTEST-APP-002"))
                .thenReturn(Optional.of(refundableApplication(1L, "PAID")));
        when(paymentRepository.findLatestPaidPayment(2L)).thenReturn(Optional.of(paidPayment()));
        when(paymentRepository.findLatestRefundByApplicationId(2L)).thenReturn(Optional.empty());
        when(paymentRepository.createRefund(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq(2L),
                org.mockito.ArgumentMatchers.eq(6L),
                org.mockito.ArgumentMatchers.eq(new BigDecimal("1700")),
                org.mockito.ArgumentMatchers.eq("TEST")))
                .thenReturn(11L);

        ApiResponse<VendorRefundResponse> response = service.requestRefund("Bearer token", request);

        assertThat(response.isSuccessStatus()).isTrue();
        assertThat(response.getData().getRefundId()).isEqualTo(11L);
        assertThat(response.getData().getApplicationId()).isEqualTo(2L);
        assertThat(response.getData().getApplicationNo()).isEqualTo("PAYTEST-APP-002");
        assertThat(response.getData().getPaymentId()).isEqualTo(6L);
        assertThat(response.getData().getPaymentNo()).isEqualTo("PAY20260716151711D40FD");
        assertThat(response.getData().getMerchantOrderNo()).isEqualTo("PAY20260716151711D40FD");
        assertThat(response.getData().getProviderTradeNo()).isEqualTo("2607161029099863");
        assertThat(response.getData().getRefundAmount()).isEqualByComparingTo("1700");
        assertThat(response.getData().getDepositAmount()).isEqualByComparingTo("1000");
        assertThat(response.getData().getRefundStatus()).isEqualTo("REFUND_REQUESTED");
        verify(notificationService).notifyRefundRequested(99L, 11L, "金流測試市集");
        verify(notificationService).notifyVendorRefundRequested(1L, 11L, "金流測試市集");
    }

    private void mockVendorToken() {
        when(jwtService.extractTokenFromAuthorizationHeader("Bearer token")).thenReturn("token");
        when(jwtService.isTokenValid("token")).thenReturn(true);
        when(jwtService.getRole("token")).thenReturn("VENDOR");
        when(jwtService.getEmail("token")).thenReturn("vendor@test.local");
    }

    private VendorRefundRequest request() {
        VendorRefundRequest request = new VendorRefundRequest();
        request.setApplicationNo("PAYTEST-APP-002");
        request.setReason("TEST");
        return request;
    }

    private Map<String, Object> refundableApplication(Long userId, String paymentStatus) {
        return Map.of(
                "applicationId", 2L,
                "applicationNo", "PAYTEST-APP-002",
                "userId", userId,
                "depositAmount", new BigDecimal("1000"),
                "reviewStatus", "APPROVED",
                "paymentStatus", paymentStatus,
                "isCancelled", false,
                "organizerUserId", 99L,
                "eventName", "金流測試市集");
    }

    private Map<String, Object> paidPayment() {
        return Map.of(
                "paymentId", 6L,
                "paymentNo", "PAY20260716151711D40FD",
                "amount", new BigDecimal("2700"),
                "provider", "NEWEBPAY",
                "providerTradeNo", "2607161029099863");
    }
}
