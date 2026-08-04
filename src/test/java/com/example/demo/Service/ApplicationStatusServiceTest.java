package com.example.demo.Service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class ApplicationStatusServiceTest {

    private final ApplicationStatusService service = new ApplicationStatusService();

    @Test
    void cancellationHasHighestPriority() {
        Map<String, Object> application = baseApplication();
        application.put("isCancelled", true);
        application.put("refundStatus", "REFUNDED");

        String cancelled = service.resolveApplicationStatus(application);
        application.put("isCancelled", false);

        assertThat(cancelled).isNotBlank().isNotEqualTo(service.resolveApplicationStatus(application));
    }

    @Test
    void refundStatesOverrideReviewAndPaymentStates() {
        Map<String, Object> application = baseApplication();
        application.put("refundStatus", "REFUNDED");
        String refunded = service.resolveApplicationStatus(application);
        application.put("refundStatus", "REFUNDING");
        String refunding = service.resolveApplicationStatus(application);
        application.put("refundStatus", "REFUND_REQUESTED");
        String requested = service.resolveApplicationStatus(application);

        assertThat(refunded).isNotBlank();
        assertThat(refunding).isNotBlank().isNotEqualTo(refunded);
        assertThat(requested).isNotBlank().isNotEqualTo(refunded);
    }

    @Test
    void paidApplicationChangesAfterAllDatesSelectAStall() {
        Map<String, Object> application = baseApplication();
        application.put("reviewStatus", "APPROVED");
        application.put("paymentStatus", "PAID");
        application.put("applicationDateCount", 2);
        application.put("selectedStallCount", 1);
        String awaitingSelection = service.resolveApplicationStatus(application);
        application.put("selectedStallCount", 2);
        String completed = service.resolveApplicationStatus(application);

        assertThat(awaitingSelection).isNotBlank();
        assertThat(completed).isNotBlank().isNotEqualTo(awaitingSelection);
    }

    @Test
    void paidApplicationWithReturnedDepositGetsFinalStateRegardlessOfEventDate() {
        Map<String, Object> application = baseApplication();
        application.put("reviewStatus", "APPROVED");
        application.put("paymentStatus", "PAID");
        application.put("selectedStallId", 1L);
        application.put("eventEndAt", LocalDate.now().plusDays(30));
        application.put("depositStatus", "RETURNED");
        String returned = service.resolveApplicationStatus(application);
        application.put("depositStatus", "NOT_RETURNED");

        assertThat(returned).isNotBlank().isNotEqualTo(service.resolveApplicationStatus(application));
    }

    private Map<String, Object> baseApplication() {
        Map<String, Object> application = new HashMap<>();
        application.put("isCancelled", false);
        application.put("reviewStatus", "PENDING");
        application.put("paymentStatus", "PENDING");
        return application;
    }
}
