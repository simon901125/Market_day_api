package com.example.demo.Service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

class PaymentStatusServiceTest {

    private final PaymentStatusService service = new PaymentStatusService();

    @Test
    void mapsEveryPaymentStateAndUnknownToNull() {
        String paid = service.resolvePaymentStatus(Map.of("paymentStatus", "PAID"));
        String pending = service.resolvePaymentStatus(Map.of("paymentStatus", "PENDING"));
        String failed = service.resolvePaymentStatus(Map.of("paymentStatus", "FAILED"));
        String expired = service.resolvePaymentStatus(Map.of("paymentStatus", "EXPIRED"));

        assertThat(paid).isNotBlank();
        assertThat(pending).isNotBlank().isNotEqualTo(paid);
        assertThat(failed).isNotBlank().isNotEqualTo(paid);
        assertThat(expired).isNotBlank().isNotEqualTo(pending);
        assertThat(service.resolvePaymentStatus(Map.of("paymentStatus", "UNKNOWN"))).isNull();
    }

    @Test
    void refundStateOverridesPaymentState() {
        String ordinaryPaid = service.resolvePaymentStatus(Map.of("paymentStatus", "PAID"));
        String refunded = service.resolvePaymentStatus(Map.of(
                "paymentStatus", "PAID",
                "refundStatus", "REFUNDED"));

        assertThat(refunded).isNotBlank().isNotEqualTo(ordinaryPaid);
    }
}
