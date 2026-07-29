package com.example.demo.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.demo.Config.NewebPayProperties;
import com.example.demo.Repository.PaymentRepository;
import com.example.demo.Repository.OrganizerPaymentAccountRepository;
import com.example.demo.dto.request.VendorPaymentRequest;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class NewebPayServiceTest {
    @Mock PaymentRepository paymentRepository;
    @Mock OrganizerPaymentAccountRepository organizerPaymentAccountRepository;
    @Mock CredentialEncryptionService credentialEncryptionService;
    @Mock NotificationService notificationService;
    @Mock JwtService jwtService;
    NewebPayService service;
    NewebPayProperties properties;

    @BeforeEach void setUp() {
        service = new NewebPayService();
        properties = new NewebPayProperties();
        ReflectionTestUtils.setField(service, "newebPayProperties", properties);
        ReflectionTestUtils.setField(service, "paymentRepository", paymentRepository);
        ReflectionTestUtils.setField(service, "organizerPaymentAccountRepository", organizerPaymentAccountRepository);
        ReflectionTestUtils.setField(service, "credentialEncryptionService", credentialEncryptionService);
        ReflectionTestUtils.setField(service, "notificationService", notificationService);
        ReflectionTestUtils.setField(service, "jwtService", jwtService);
        ReflectionTestUtils.setField(service, "objectMapper", new ObjectMapper());
    }

    @Test void createPaymentRequiresApplicationNumberBeforeAuthentication() {
        assertThat(service.createPayment(null, null).isSuccessStatus()).isFalse();
        VendorPaymentRequest request = new VendorPaymentRequest();
        request.setApplicationNo(" ");
        assertThat(service.createPayment(null, request).isSuccessStatus()).isFalse();
    }

    @Test void protectedOperationsRejectInvalidToken() {
        VendorPaymentRequest request = new VendorPaymentRequest();
        request.setApplicationNo("APP-1");
        when(jwtService.extractTokenFromAuthorizationHeader("Bearer bad")).thenReturn("bad");
        when(jwtService.isTokenValid("bad")).thenReturn(false);
        assertThat(service.createPayment("Bearer bad", request).isSuccessStatus()).isFalse();
        assertThat(service.getPaymentStatus("Bearer bad", "APP-1").isSuccessStatus()).isFalse();
    }

    @Test void createPaymentRejectsIncompleteProviderConfiguration() {
        VendorPaymentRequest request = new VendorPaymentRequest();
        request.setApplicationNo("APP-1");
        when(jwtService.extractTokenFromAuthorizationHeader("Bearer token")).thenReturn("token");
        when(jwtService.isTokenValid("token")).thenReturn(true);
        when(jwtService.getRole("token")).thenReturn("VENDOR");
        assertThat(service.createPayment("Bearer token", request).isSuccessStatus()).isFalse();
    }

    @Test void invalidCallbackBuildsSafeFrontendFallback() {
        String url = service.buildVendorReturnUrl(Map.of(), "https://front.test/");
        assertThat(url).isEqualTo("https://front.test/vendor/dash-board/application-record?paymentStatus=invalid");
    }

    @Test void organizerVerificationReturnDoesNotFallThroughToVendorRoute() {
        String merchantId = "TEST_MERCHANT";
        String hashKey = "12345678901234567890123456789012";
        String hashIv = "1234567890123456";
        String verificationNo = "NPV20260728123000ABCD";
        Map<String, String> payload = signedCallback(
                merchantId, hashKey, hashIv, verificationNo, "SUCCESS", "1");
        mockCallbackAccount(merchantId, hashKey, hashIv);

        String url = service.buildOrganizerVerificationReturnUrl(
                payload, "http://localhost:4200/");

        assertThat(url).isEqualTo(
                "http://localhost:4200/organizer/dash-board/home"
                        + "?verificationNo=" + verificationNo
                        + "&verificationStatus=SUCCESS");
        verify(organizerPaymentAccountRepository, org.mockito.Mockito.never())
                .findVerificationByNo(verificationNo);
        verify(paymentRepository, org.mockito.Mockito.never())
                .findPaymentWithApplication(verificationNo);
    }

    @Test void vendorPaymentReturnUsesApplicationPaymentRoute() {
        String merchantId = "TEST_MERCHANT";
        String hashKey = "12345678901234567890123456789012";
        String hashIv = "1234567890123456";
        String paymentNo = "MDP20260728153000ABCD";
        Map<String, String> payload = signedCallback(
                merchantId, hashKey, hashIv, paymentNo, "FAILED", "350");
        mockCallbackAccount(merchantId, hashKey, hashIv);
        when(paymentRepository.findPaymentWithApplication(paymentNo))
                .thenReturn(Optional.of(Map.of(
                        "paymentAccountId", 9L,
                        "paymentNo", paymentNo,
                        "applicationId", 27L,
                        "applicationNo", "APP-20260728-001",
                        "paymentRecordStatus", "PENDING")));

        String url = service.buildVendorReturnUrl(payload, "http://localhost:4200/");

        assertThat(url).isEqualTo(
                "http://localhost:4200/vendor/dash-board/application-record/detail/APP-20260728-001/payment"
                        + "?applicationId=27"
                        + "&paymentNo=" + paymentNo
                        + "&merchantOrderNo=" + paymentNo
                        + "&status=FAILED");
        verify(paymentRepository).markPaymentFailed(paymentNo, null, "FAILED", null);
    }

    private Map<String, String> signedCallback(
            String merchantId,
            String hashKey,
            String hashIv,
            String orderNo,
            String status,
            String amount) {
        String plainTradeInfo = "Status=" + status
                + "&MerchantID=" + merchantId
                + "&MerchantOrderNo=" + orderNo
                + "&Amt=" + amount;
        String encryptedTradeInfo = ReflectionTestUtils.invokeMethod(
                service, "encrypt", plainTradeInfo, hashKey, hashIv);
        String tradeSha = ReflectionTestUtils.invokeMethod(
                service,
                "sha256Upper",
                "HashKey=" + hashKey + "&" + encryptedTradeInfo + "&HashIV=" + hashIv);
        return Map.of(
                "MerchantID", merchantId,
                "TradeInfo", encryptedTradeInfo,
                "TradeSha", tradeSha);
    }

    private void mockCallbackAccount(String merchantId, String hashKey, String hashIv) {
        when(organizerPaymentAccountRepository.findByMerchantId(merchantId))
                .thenReturn(Optional.of(Map.of(
                        "paymentAccountId", 9L,
                        "merchantId", merchantId,
                        "status", "ACTIVE",
                        "verificationStatus", "VERIFIED",
                        "hashKeyEncrypted", "encrypted-key",
                        "hashIvEncrypted", "encrypted-iv")));
        when(credentialEncryptionService.decrypt("encrypted-key")).thenReturn(hashKey);
        when(credentialEncryptionService.decrypt("encrypted-iv")).thenReturn(hashIv);
    }

    @Test void notifyRejectsMissingTradeInformation() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.handleNotify(Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TradeInfo");
    }

    @Test void organizerNotifyDoesNotFallThroughToVendorPaymentLookup() {
        String merchantId = "TEST_MERCHANT";
        String hashKey = "12345678901234567890123456789012";
        String hashIv = "1234567890123456";
        String verificationNo = "NPV20260728170000ABCD";
        mockCallbackAccount(merchantId, hashKey, hashIv);
        when(organizerPaymentAccountRepository.findVerificationByNo(verificationNo))
                .thenReturn(Optional.empty());

        String result = service.handleNotify(signedCallback(
                merchantId, hashKey, hashIv, verificationNo, "SUCCESS", "1"));

        assertThat(result).isEqualTo("0|Verification not found");
        verify(paymentRepository, org.mockito.Mockito.never())
                .findPaymentWithApplication(verificationNo);
    }

    @Test void createPaymentUsesEventOrganizerMerchantAndCopiesPaymentAccount() {
        properties.setGateway("https://ccore.newebpay.com/MPG/mpg_gateway");
        properties.setNotifyUrl("https://api.test/api/newebpay/notify");
        properties.setReturnUrl("https://api.test/api/newebpay/return");
        properties.setOrganizerVerificationReturnUrl(
                "https://api.test/api/newebpay/organizer-verification/return");
        properties.setVersion("2.3");
        when(jwtService.extractTokenFromAuthorizationHeader("Bearer token")).thenReturn("token");
        when(jwtService.isTokenValid("token")).thenReturn(true);
        when(jwtService.getRole("token")).thenReturn("VENDOR");
        when(jwtService.getEmail("token")).thenReturn("vendor@test");
        when(paymentRepository.findVendorPaymentUserByEmail("vendor@test"))
                .thenReturn(Optional.of(Map.of("userId", 3L)));
        when(paymentRepository.findPayableApplication("MD001"))
                .thenReturn(Optional.of(Map.ofEntries(
                        Map.entry("applicationId", 7L),
                        Map.entry("applicationNo", "MD001"),
                        Map.entry("userId", 3L),
                        Map.entry("totalAmount", BigDecimal.valueOf(110)),
                        Map.entry("reviewStatus", "APPROVED"),
                        Map.entry("paymentStatus", "PENDING"),
                        Map.entry("isCancelled", false),
                        Map.entry("eventName", "測試活動"),
                        Map.entry("paymentAccountId", 12L),
                        Map.entry("merchantId", "MS123456789"),
                        Map.entry("hashKeyEncrypted", "encrypted-key"),
                        Map.entry("hashIvEncrypted", "encrypted-iv"),
                        Map.entry("paymentAccountStatus", "ACTIVE"),
                        Map.entry("paymentAccountVerificationStatus", "VERIFIED"))));
        when(credentialEncryptionService.decrypt("encrypted-key"))
                .thenReturn("12345678901234567890123456789012");
        when(credentialEncryptionService.decrypt("encrypted-iv"))
                .thenReturn("1234567890123456");
        when(paymentRepository.findLatestPendingPayment(7L))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(Map.of(
                        "paymentId", 20L,
                        "paymentNo", "PAY001",
                        "paymentAccountId", 12L)));

        VendorPaymentRequest request = new VendorPaymentRequest();
        request.setApplicationNo("MD001");
        var response = service.createPayment("Bearer token", request);

        assertThat(response.isSuccessStatus()).isTrue();
        assertThat(response.getData().getMerchantId()).isEqualTo("MS123456789");
        verify(paymentRepository).createPendingPayment(
                anyString(), eq(7L), eq(12L), eq(BigDecimal.valueOf(110)));
    }
}
