package com.example.demo.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.demo.Repository.PaymentRepository;
import com.example.demo.dto.request.OrganizerRefundRequest;
import com.example.demo.dto.response.ApiResponse;
import com.example.demo.dto.response.NewebPayRefundResultResponse;
import com.example.demo.dto.response.OrganizerRefundResponse;

@ExtendWith(MockitoExtension.class)
class OrganizerRefundServiceTest {

    @Mock PaymentRepository paymentRepository;
    @Mock JwtService jwtService;
    @Mock NewebPayService newebPayService;
    @Mock NotificationService notificationService;

    OrganizerRefundService service;

    @BeforeEach
    void setUp() {
        service = new OrganizerRefundService();
        ReflectionTestUtils.setField(service, "paymentRepository", paymentRepository);
        ReflectionTestUtils.setField(service, "jwtService", jwtService);
        ReflectionTestUtils.setField(service, "newebPayService", newebPayService);
        ReflectionTestUtils.setField(service, "notificationService", notificationService);
    }

    @Test
    void reviewRefundRejectsNonOrganizerToken() {
        OrganizerRefundRequest request = request();
        when(jwtService.extractTokenFromAuthorizationHeader("Bearer token")).thenReturn("token");
        when(jwtService.isTokenValid("token")).thenReturn(true);
        when(jwtService.getRole("token")).thenReturn("VENDOR");

        ApiResponse<OrganizerRefundResponse> response = service.reviewRefund("Bearer token", request);

        assertThat(response.isSuccessStatus()).isFalse();
        assertThat(response.getStatusCode()).isEqualTo(403);
    }

    @Test
    void reviewRefundRejectsRefundOwnedByAnotherOrganizer() {
        mockOrganizerToken();
        when(paymentRepository.findOrganizerPaymentUserByEmail("organizer@test.local"))
                .thenReturn(Optional.of(Map.of("userId", 99L)));
        when(paymentRepository.findRefundForOrganizerProcessing("REF001"))
                .thenReturn(Optional.of(refund("REFUND_REQUESTED", 100L)));

        ApiResponse<OrganizerRefundResponse> response = service.reviewRefund("Bearer token", request());

        assertThat(response.isSuccessStatus()).isFalse();
        verify(newebPayService, never()).closeCreditCardRefund(any(), any(), any(), any());
    }

    @Test
    void reviewRefundOnlyAllowsRequestedStatus() {
        mockOrganizerToken();
        when(paymentRepository.findOrganizerPaymentUserByEmail("organizer@test.local"))
                .thenReturn(Optional.of(Map.of("userId", 99L)));
        when(paymentRepository.findRefundForOrganizerProcessing("REF001"))
                .thenReturn(Optional.of(refund("REFUNDED", 99L)));

        ApiResponse<OrganizerRefundResponse> response = service.reviewRefund("Bearer token", request());

        assertThat(response.isSuccessStatus()).isFalse();
        verify(paymentRepository, never()).markRefundProcessing(any());
    }
    @Test
    void reviewRefundDoesNotCallNewebPayWhenRefunding() {
        mockOrganizerToken();
        when(paymentRepository.findOrganizerPaymentUserByEmail("organizer@test.local"))
                .thenReturn(Optional.of(Map.of("userId", 99L)));
        when(paymentRepository.findRefundForOrganizerProcessing("REF001"))
                .thenReturn(Optional.of(refund("REFUNDING", 99L)));

        ApiResponse<OrganizerRefundResponse> response = service.reviewRefund("Bearer token", request());

        assertThat(response.isSuccessStatus()).isFalse();
        assertThat(response.getMessage()).isEqualTo("\u9000\u6B3E\u5DF2\u5728\u8655\u7406\u4E2D\uFF0C\u8ACB\u52FF\u91CD\u8907\u78BA\u8A8D");
        verify(paymentRepository, never()).markRefundProcessing(any());
        verify(newebPayService, never()).closeCreditCardRefund(any(), any(), any(), any());
    }

    @Test
    void reviewRefundDoesNotCallNewebPayWhenRefundFailed() {
        mockOrganizerToken();
        when(paymentRepository.findOrganizerPaymentUserByEmail("organizer@test.local"))
                .thenReturn(Optional.of(Map.of("userId", 99L)));
        when(paymentRepository.findRefundForOrganizerProcessing("REF001"))
                .thenReturn(Optional.of(refund("REFUND_FAILED", 99L)));

        ApiResponse<OrganizerRefundResponse> response = service.reviewRefund("Bearer token", request());

        assertThat(response.isSuccessStatus()).isFalse();
        assertThat(response.getMessage()).isEqualTo("\u9000\u6B3E\u66FE\u57F7\u884C\u5931\u6557\uFF0C\u8ACB\u4F7F\u7528\u9000\u6B3E\u91D1\u6D41\u91CD\u8A66 API");
        verify(paymentRepository, never()).markRefundProcessing(any());
        verify(newebPayService, never()).closeCreditCardRefund(any(), any(), any(), any());
    }

    @Test
    void reviewRefundDoesNotCallNewebPayWhenRefunded() {
        mockOrganizerToken();
        when(paymentRepository.findOrganizerPaymentUserByEmail("organizer@test.local"))
                .thenReturn(Optional.of(Map.of("userId", 99L)));
        when(paymentRepository.findRefundForOrganizerProcessing("REF001"))
                .thenReturn(Optional.of(refund("REFUNDED", 99L)));

        ApiResponse<OrganizerRefundResponse> response = service.reviewRefund("Bearer token", request());

        assertThat(response.isSuccessStatus()).isFalse();
        assertThat(response.getMessage()).isEqualTo("\u6B64\u9000\u6B3E\u5DF2\u5B8C\u6210\uFF0C\u4E0D\u53EF\u91CD\u8907\u9000\u6B3E");
        verify(paymentRepository, never()).markRefundProcessing(any());
        verify(newebPayService, never()).closeCreditCardRefund(any(), any(), any(), any());
    }

    @Test
    void retryRefundPaymentRejectsRequestedStatus() {
        mockOrganizerToken();
        when(paymentRepository.findOrganizerPaymentUserByEmail("organizer@test.local"))
                .thenReturn(Optional.of(Map.of("userId", 99L)));
        when(paymentRepository.findRefundForOrganizerProcessing("REF001"))
                .thenReturn(Optional.of(refund("REFUND_REQUESTED", 99L)));

        ApiResponse<OrganizerRefundResponse> response = service.retryRefundPayment("Bearer token", request());

        assertThat(response.isSuccessStatus()).isFalse();
        verify(paymentRepository, never()).markRefundProcessing(any());
        verify(newebPayService, never()).closeCreditCardRefund(any(), any(), any(), any());
    }

    @Test
    void retryRefundPaymentAllowsFailedStatus() {
        mockOrganizerToken();
        when(paymentRepository.findOrganizerPaymentUserByEmail("organizer@test.local"))
                .thenReturn(Optional.of(Map.of("userId", 99L)));
        when(paymentRepository.findRefundForOrganizerProcessing("REF001"))
                .thenReturn(Optional.of(refund("REFUND_FAILED", 99L)))
                .thenReturn(Optional.of(refund("REFUNDED", 99L)));
        when(newebPayService.closeCreditCardRefund("PAY001", "26071714554003655", new BigDecimal("700"), new BigDecimal("1700")))
                .thenReturn(new NewebPayRefundResultResponse(
                        "MS159696944",
                        new BigDecimal("700"),
                        "26071714554003655",
                        "PAY001"));

        ApiResponse<OrganizerRefundResponse> response = service.retryRefundPayment("Bearer token", request());

        assertThat(response.isSuccessStatus()).isTrue();
        assertThat(response.getData().getRefundStatus()).isEqualTo("REFUNDED");
        verify(paymentRepository).markRefundProcessing(2L);
        verify(notificationService).notifyRefundProcessingToVendor(11L, 2L, "Test Market");
        verify(paymentRepository).markRefundSucceeded(2L);
        verify(notificationService).notifyRefundSucceededToVendor(11L, 2L, "Test Market");
        verify(notificationService).notifyRefundSucceededToOrganizer(99L, 2L, "Test Market");
    }

    @Test
    void reviewRefundMarksFailedWhenNewebPayFails() {
        mockOrganizerToken();
        when(paymentRepository.findOrganizerPaymentUserByEmail("organizer@test.local"))
                .thenReturn(Optional.of(Map.of("userId", 99L)));
        when(paymentRepository.findRefundForOrganizerProcessing("REF001"))
                .thenReturn(Optional.of(refund("REFUND_REQUESTED", 99L)))
                .thenReturn(Optional.of(refund("REFUND_FAILED", 99L)));
        when(newebPayService.closeCreditCardRefund("PAY001", "26071714554003655", new BigDecimal("700"), new BigDecimal("1700")))
                .thenThrow(new IllegalStateException("Close failed"));

        ApiResponse<OrganizerRefundResponse> response = service.reviewRefund("Bearer token", request());

        assertThat(response.isSuccessStatus()).isFalse();
        assertThat(response.getData().getRefundStatus()).isEqualTo("REFUND_FAILED");
        verify(paymentRepository).markRefundFailed(2L, "Close failed");
        verify(notificationService).notifyRefundProcessingToVendor(11L, 2L, "Test Market");
        verify(notificationService).notifyRefundFailedToVendor(11L, 2L, "Test Market");
        verify(notificationService).notifyRefundFailedToOrganizer(99L, 2L, "Test Market");
    }

    private void mockOrganizerToken() {
        when(jwtService.extractTokenFromAuthorizationHeader("Bearer token")).thenReturn("token");
        when(jwtService.isTokenValid("token")).thenReturn(true);
        when(jwtService.getRole("token")).thenReturn("ORGANIZER");
        when(jwtService.getEmail("token")).thenReturn("organizer@test.local");
    }

    private OrganizerRefundRequest request() {
        OrganizerRefundRequest request = new OrganizerRefundRequest();
        request.setRefundNo("REF001");
        return request;
    }

    private Map<String, Object> refund(String refundStatus, Long organizerUserId) {
        Map<String, Object> refund = new HashMap<>();
        refund.put("refundId", 2L);
        refund.put("refundNo", "REF001");
        refund.put("refundAmount", new BigDecimal("700"));
        refund.put("refundStatus", refundStatus);
        refund.put("applicationId", 3L);
        refund.put("applicationNo", "APP001");
        refund.put("vendorUserId", 11L);
        refund.put("organizerUserId", organizerUserId);
        refund.put("eventName", "Test Market");
        refund.put("paymentId", 4L);
        refund.put("paymentNo", "PAY001");
        refund.put("providerTradeNo", "26071714554003655");
        refund.put("provider", "NEWEBPAY");
        refund.put("paymentStatus", "PAID");
        refund.put("paymentAmount", new BigDecimal("1700"));
        return refund;
    }
}
