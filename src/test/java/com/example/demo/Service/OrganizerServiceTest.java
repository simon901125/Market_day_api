package com.example.demo.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.demo.Repository.OrganizerRepository;

@ExtendWith(MockitoExtension.class)
class OrganizerServiceTest {
    @Mock OrganizerRepository repository;
    @Mock JwtService jwtService;
    @Mock ApplicationStatusService applicationStatusService;
    @Mock TaiwanAddressService addressService;
    OrganizerService service;

    @BeforeEach void setUp() {
        service = new OrganizerService();
        ReflectionTestUtils.setField(service, "organizerRepository", repository);
        ReflectionTestUtils.setField(service, "jwtService", jwtService);
        ReflectionTestUtils.setField(service, "applicationStatusService", applicationStatusService);
        ReflectionTestUtils.setField(service, "taiwanAddressService", addressService);
    }

    @Test void profileOperationsValidateBodyAndAuthorization() {
        assertThat(service.loadOrganizerProfile(null).isSuccessStatus()).isFalse();
        assertThat(service.saveOrganizerProfile("Bearer token", null).isSuccessStatus()).isFalse();
    }

    @Test void allSearchAndDetailOperationsRejectMissingAuthorization() {
        assertThat(service.searchOrganizerAccounts(null, null, null, null, null, 1, 20).isSuccessStatus()).isFalse();
        assertThat(service.getOrganizerAccountDetail(null, 1L, null, 1, 10).isSuccessStatus()).isFalse();
        assertThat(service.searchOrganizerApplications(null, null, null, null, null, null, 1, 20).isSuccessStatus()).isFalse();
        assertThat(service.searchOrganizerStallEvents(null, null, null, null, null, 1, 20).isSuccessStatus()).isFalse();
        assertThat(service.searchOrganizerEquipmentEvents(null, null, null, null, null, 1, 20).isSuccessStatus()).isFalse();
        assertThat(service.getOrganizerEquipmentDetail(null, 1L, 1, 10, 1, 10, 1, 10).isSuccessStatus()).isFalse();
        assertThat(service.getOrganizerApplicationDetail(null, 1L).isSuccessStatus()).isFalse();
        assertThat(service.approveOrganizerApplication(null, 1L).isSuccessStatus()).isFalse();
        assertThat(service.rejectOrganizerApplication(null, 1L, null).isSuccessStatus()).isFalse();
        assertThat(service.refundOrganizerDeposit(null, 1L).isSuccessStatus()).isFalse();
    }

    @Test void exportsReturnFailureForMissingAuthorization() {
        assertThat(service.exportOrganizerAccountReport(null, 1L, null).success()).isFalse();
        assertThat(service.exportOrganizerEquipmentReport(null, 1L).success()).isFalse();
    }

    @Test void reportExportFactoriesCarryExpectedMetadata() {
        byte[] bytes = {1, 2};
        var excel = OrganizerService.ReportExport.excel("report.xlsx", bytes);
        var fail = OrganizerService.ReportExport.fail("bad");
        assertThat(excel.success()).isTrue();
        assertThat(excel.content()).isSameAs(bytes);
        assertThat(excel.contentType()).contains("spreadsheet");
        assertThat(fail.success()).isFalse();
        assertThat(fail.errorMessage()).isEqualTo("bad");
    }

    @Test void sharedApplicationDetailContainsAllVendorScreenSections() {
        Long applicationId = 8L;
        Map<String, Object> application = new LinkedHashMap<>();
        application.put("applicationId", applicationId);
        application.put("applicationNo", "APP-008");
        application.put("eventId", 18L);
        application.put("eventTitle", "Integration Market");
        application.put("workflowStatus", "UNPUBLISH_REQUESTED");
        application.put("eventCoverImageUrl", "/images/event.jpg");
        application.put("eventCity", "台北市");
        application.put("eventDistrict", "信義區");
        application.put("locationName", "市民廣場");
        application.put("eventAddress", "市府路 1 號");
        application.put("eventStartAt", LocalDateTime.of(2026, 8, 1, 10, 0));
        application.put("eventEndAt", LocalDateTime.of(2026, 8, 2, 18, 0));
        application.put("registrationStartAt", LocalDateTime.of(2026, 6, 1, 0, 0));
        application.put("registrationEndAt", LocalDateTime.of(2026, 7, 1, 0, 0));
        application.put("baseFee", new BigDecimal("1000"));
        application.put("depositAmount", new BigDecimal("500"));
        application.put("totalAmount", new BigDecimal("1500"));
        application.put("reviewStatus", "APPROVED");
        application.put("paymentStatus", "PAID");
        application.put("paymentProvider", "NEWEBPAY");
        application.put("paymentNo", "PAY-008");
        application.put("refundStatus", "REFUNDED");
        application.put("refundNo", "REF-008");
        application.put("refundAmount", new BigDecimal("1500"));
        application.put("appliedAt", LocalDateTime.of(2026, 6, 1, 14, 30));
        application.put("applicationDateCount", 1);
        application.put("selectedStallCount", 1);

        when(applicationStatusService.resolveApplicationStatus(anyMap())).thenReturn("已退款");
        when(repository.findApplicationDates(applicationId)).thenReturn(List.of(Map.of(
                "applicationDateId", 1L,
                "applyDate", LocalDate.of(2026, 8, 1),
                "selectedStallId", 2L,
                "stallNo", "A01",
                "zoneName", "A區",
                "width", new BigDecimal("3"),
                "length", new BigDecimal("3"))));
        when(repository.findApplicationEquipmentRentals(applicationId)).thenReturn(List.of());
        when(repository.findApplicationStatusLogs(applicationId)).thenReturn(List.of());

        Map<String, Object> response = service.buildApplicationDetailResponse(applicationId, application);

        assertThat(response).containsKeys(
                "application", "event", "status", "applicationdetail", "fee", "refund",
                "stall", "equipmentRentals", "feedetail");
        assertThat((Map<String, Object>) response.get("event"))
                .containsEntry("eventCoverImageUrl", "/images/event.jpg")
                .containsEntry("locationName", "台北市信義區市民廣場")
                .containsEntry("workflowStatus", "UNPUBLISH_REQUESTED")
                .containsEntry("unpublishRequested", true)
                .containsEntry("unpublished", false);
        assertThat((Map<String, Object>) response.get("refund"))
                .containsEntry("refundStatusText", "已退款")
                .containsEntry("refundNo", "REF-008");
        assertThat((Map<String, Object>) response.get("equipmentRentals"))
                .containsKeys("freeEquipments", "freeBasicPower", "rentalEquipments", "extraPower");
    }
}
