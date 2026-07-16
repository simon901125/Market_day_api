package com.example.demo.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.demo.Config.NewebPayProperties;
import com.example.demo.Repository.PaymentRepository;
import com.example.demo.dto.request.VendorPaymentRequest;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class NewebPayServiceTest {
    @Mock PaymentRepository paymentRepository;
    @Mock JwtService jwtService;
    NewebPayService service;
    NewebPayProperties properties;

    @BeforeEach void setUp() {
        service = new NewebPayService();
        properties = new NewebPayProperties();
        ReflectionTestUtils.setField(service, "newebPayProperties", properties);
        ReflectionTestUtils.setField(service, "paymentRepository", paymentRepository);
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
        String url = service.buildReturnUrl(Map.of(), "https://front.test/");
        assertThat(url).isEqualTo("https://front.test/vendor/dash-board/application-record?paymentStatus=invalid");
    }

    @Test void notifyRejectsMissingTradeInformation() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.handleNotify(Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TradeInfo");
    }
}
