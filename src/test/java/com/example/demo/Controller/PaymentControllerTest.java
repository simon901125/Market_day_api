package com.example.demo.Controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;

import com.example.demo.Service.NewebPayService;
import com.example.demo.Service.VendorRefundService;
import com.example.demo.dto.request.VendorPaymentRequest;
import com.example.demo.dto.request.VendorRefundRequest;

@ExtendWith(MockitoExtension.class)
class PaymentControllerTest {
    @Mock NewebPayService service;
    @Mock VendorRefundService vendorRefundService;

    @Test void vendorPaymentEndpointsDelegate() {
        PaymentController controller = new PaymentController(service, vendorRefundService, "https://front.test");
        VendorPaymentRequest request = new VendorPaymentRequest();
        controller.createNewebPayPayment("Bearer token", request);
        controller.getPaymentStatus("Bearer token", "APP-1");
        controller.queryNewebPayTrade("Bearer token", "APP-1");
        verify(service).createPayment("Bearer token", request);
        verify(service).getPaymentStatus("Bearer token", "APP-1");
        verify(service).queryNewebPayTrade("Bearer token", "APP-1");
    }

    @Test void vendorRefundEndpointDelegates() {
        PaymentController controller = new PaymentController(service, vendorRefundService, "https://front.test");
        VendorRefundRequest request = new VendorRefundRequest();
        controller.requestVendorRefund("Bearer token", request);
        verify(vendorRefundService).requestRefund("Bearer token", request);
    }

    @Test void notifyReturnsProviderResultAndConvertsRuntimeFailure() {
        PaymentController controller = new PaymentController(service, vendorRefundService, "https://front.test");
        Map<String, String> payload = Map.of("TradeInfo", "value");
        when(service.handleNotify(payload)).thenReturn("1|OK");
        assertThat(controller.receiveNotify(payload)).isEqualTo("1|OK");
        when(service.handleNotify(payload)).thenThrow(new IllegalArgumentException("bad payload"));
        assertThat(controller.receiveNotify(payload)).isEqualTo("0|bad payload");
    }

    @Test void getAndPostReturnRedirectToFrontendUrlBuiltByService() {
        PaymentController controller = new PaymentController(service, vendorRefundService, "https://front.test");
        Map<String, String> payload = Map.of("Status", "SUCCESS");
        when(service.buildReturnUrl(eq(payload), any())).thenReturn("https://front.test/payment/result");
        assertThat(controller.receiveReturn(payload).getStatusCode().value()).isEqualTo(302);
        assertThat(controller.openReturn(payload).getHeaders().getFirst(HttpHeaders.LOCATION))
                .isEqualTo("https://front.test/payment/result");
        verify(service, org.mockito.Mockito.times(2)).buildReturnUrl(payload, "https://front.test");
    }
}
