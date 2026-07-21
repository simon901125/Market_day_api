package com.example.demo.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
    @Mock NotificationService notificationService;
    OrganizerService service;

    @BeforeEach void setUp() {
        service = new OrganizerService();
        ReflectionTestUtils.setField(service, "organizerRepository", repository);
        ReflectionTestUtils.setField(service, "jwtService", jwtService);
        ReflectionTestUtils.setField(service, "applicationStatusService", applicationStatusService);
        ReflectionTestUtils.setField(service, "taiwanAddressService", addressService);
        ReflectionTestUtils.setField(service, "notificationService", notificationService);
    }

    @Test void profileOperationsValidateBodyAndAuthorization() {
        assertThat(service.loadOrganizerProfile(null).isSuccessStatus()).isFalse();
        assertThat(service.saveOrganizerProfile("Bearer token", null).isSuccessStatus()).isFalse();
    }

    @Test void allSearchAndDetailOperationsRejectMissingAuthorization() {
        assertThat(service.searchOrganizerAccounts(null, null, null, null, null, 1, 20).isSuccessStatus()).isFalse();
        assertThat(service.searchOrganizerPayments(null, null, null, null, null, 1, 10).isSuccessStatus()).isFalse();
        assertThat(service.getOrganizerAccountDetail(null, 1L, null, 1, 10).isSuccessStatus()).isFalse();
        assertThat(service.getOrganizerPaymentDetail(null, 1L).isSuccessStatus()).isFalse();
        assertThat(service.searchOrganizerApplications(null, null, null, null, null, null, 1, 20).isSuccessStatus()).isFalse();
        assertThat(service.searchOrganizerStallEvents(null, null, null, null, null, 1, 20).isSuccessStatus()).isFalse();
        assertThat(service.searchOrganizerEquipmentEvents(null, null, null, null, null, 1, 20).isSuccessStatus()).isFalse();
        assertThat(service.getOrganizerEquipmentDetail(null, 1L, 1, 10, 1, 10, 1, 10).isSuccessStatus()).isFalse();
        assertThat(service.getOrganizerApplicationDetail(null, 1L).isSuccessStatus()).isFalse();
        assertThat(service.approveOrganizerApplication(null, 1L).isSuccessStatus()).isFalse();
        assertThat(service.rejectOrganizerApplication(null, 1L, null).isSuccessStatus()).isFalse();
        assertThat(service.refundOrganizerDeposit(null, 1L).isSuccessStatus()).isFalse();
    }

    @Test void paymentSearchFiltersAndFormatsRows() {
        when(jwtService.extractTokenFromAuthorizationHeader("Bearer token")).thenReturn("token");
        when(jwtService.isTokenValid("token")).thenReturn(true);
        when(jwtService.getEmail("token")).thenReturn("organizer@test.com");
        when(repository.findOrganizerAccountByEmail("organizer@test.com"))
                .thenReturn(Optional.of(Map.of("userId", 7L, "role", "ORGANIZER")));
        when(repository.findOrganizerPayments(
                7L,
                "咖啡",
                "PAID",
                LocalDate.of(2026, 7, 1).atStartOfDay(),
                LocalDate.of(2026, 8, 1).atStartOfDay()))
                .thenReturn(List.of(Map.of(
                        "applicationId", 12L,
                        "eventCoverImageUrl", "/images/event.jpg",
                        "eventTitle", "咖啡市集",
                        "brandName", "晨光咖啡",
                        "vendorName", "王小明",
                        "paymentAmount", new BigDecimal("1500"),
                        "depositAmount", new BigDecimal("500"),
                        "paymentTime", LocalDateTime.of(2026, 7, 15, 14, 30),
                        "paymentStatus", "PAID",
                        "paymentStage", "PAID")));
        when(applicationStatusService.resolveApplicationStatus(anyMap())).thenReturn("報名完成");

        var response = service.searchOrganizerPayments(
                "Bearer token", "咖啡", "已付款",
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31), 1, 10);

        assertThat(response.isSuccessStatus()).isTrue();
        var payments = response.getData().getPayments();
        assertThat(payments.getTotalItems()).isEqualTo(1);
        assertThat(payments.getItems().getFirst().getValues())
                .containsEntry("eventTitle", "咖啡市集")
                .containsEntry("brandName", "晨光咖啡")
                .containsEntry("vendorName", "王小明")
                .containsEntry("applicationStatus", "報名完成")
                .containsEntry("paymentTime", "2026-07-15 14:30:00")
                .containsEntry("paymentStatus", "付款成功");
    }

    @Test void paymentSearchRejectsInvalidDateRangeAndStatus() {
        when(jwtService.extractTokenFromAuthorizationHeader("Bearer token")).thenReturn("token");
        when(jwtService.isTokenValid("token")).thenReturn(true);
        when(jwtService.getEmail("token")).thenReturn("organizer@test.com");
        when(repository.findOrganizerAccountByEmail("organizer@test.com"))
                .thenReturn(Optional.of(Map.of("userId", 7L, "role", "ORGANIZER")));

        assertThat(service.searchOrganizerPayments(
                "Bearer token", null, null,
                LocalDate.of(2026, 7, 2), LocalDate.of(2026, 7, 1), 1, 10).getStatusCode())
                .isEqualTo(400);
        assertThat(service.searchOrganizerPayments(
                "Bearer token", null, "未知狀態", null, null, 1, 10).getStatusCode())
                .isEqualTo(400);
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
        when(repository.findApplicationEquipmentRentals(applicationId)).thenReturn(List.of(Map.of(
                "equipmentRentalId", 81L,
                "equipmentName", "桌子 180×60 公分",
                "equipmentDescription", "每日計費。",
                "quantity", 1,
                "pricingUnit", "DAY",
                "rentalFee", new BigDecimal("100"),
                "subtotal", new BigDecimal("200"),
                "chargeType", "PAID",
                "itemType", "EQUIPMENT")));
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
        Map<String, Object> equipmentRentals = (Map<String, Object>) response.get("equipmentRentals");
        assertThat(equipmentRentals)
                .containsKeys("freeEquipments", "freeBasicPower", "rentalEquipments", "extraPower");
        List<Map<String, Object>> rentalEquipments =
                (List<Map<String, Object>>) equipmentRentals.get("rentalEquipments");
        assertThat(rentalEquipments).hasSize(1);
        assertThat(rentalEquipments.getFirst())
                .containsEntry("unitPrice", new BigDecimal("100"));
    }
    @Test void paymentDetailContainsAllSectionsAndOmitsRefundWhenAbsent() {
        Long applicationId = 12L;
        when(jwtService.extractTokenFromAuthorizationHeader("Bearer token")).thenReturn("token");
        when(jwtService.isTokenValid("token")).thenReturn(true);
        when(jwtService.getEmail("token")).thenReturn("organizer@test.com");
        when(repository.findOrganizerAccountByEmail("organizer@test.com"))
                .thenReturn(Optional.of(Map.of("userId", 7L, "role", "ORGANIZER")));

        Map<String, Object> application = new LinkedHashMap<>();
        application.put("applicationId", applicationId);
        application.put("applicationNo", "APP-012");
        application.put("eventId", 20L);
        application.put("eventTitle", "Payment Market");
        application.put("eventStartAt", LocalDateTime.of(2026, 8, 1, 10, 0));
        application.put("eventEndAt", LocalDateTime.of(2026, 8, 2, 18, 0));
        application.put("baseFee", new BigDecimal("1000"));
        application.put("depositAmount", new BigDecimal("500"));
        application.put("totalAmount", new BigDecimal("2500"));
        application.put("reviewStatus", "APPROVED");
        application.put("paymentStatus", "PAID");
        application.put("paymentNo", "PAY-012");
        application.put("paymentProvider", "ECPAY");
        application.put("paymentProviderTradeNo", "TRADE-012");
        application.put("applicationDateCount", 2);
        application.put("selectedStallCount", 2);
        when(repository.findOrganizerApplicationDetail(7L, applicationId)).thenReturn(Optional.of(application));
        when(repository.findApplicationDates(applicationId)).thenReturn(List.of(
                Map.of("applyDate", LocalDate.of(2026, 8, 1)),
                Map.of("applyDate", LocalDate.of(2026, 8, 2))));
        when(repository.findApplicationEquipmentRentals(applicationId)).thenReturn(List.of());
        when(repository.findEventEquipments(20L)).thenReturn(List.of());
        when(repository.findApplicationStatusLogs(applicationId)).thenReturn(List.of());
        when(applicationStatusService.resolveApplicationStatus(anyMap())).thenReturn("已付款");

        var result = service.getOrganizerPaymentDetail("Bearer token", applicationId);

        assertThat(result.isSuccessStatus()).isTrue();
        assertThat(result.getData().getValues()).containsKeys(
                "event", "application", "statusRecords", "vendor", "brand", "payment",
                "feeDetails", "refund", "refundDetails", "basicEquipments", "basicPower",
                "rentalEquipments", "extraPower");
        assertThat(result.getData().getValues().get("refund")).isNull();
        assertThat(result.getData().getValues().get("refundDetails")).isNull();
    }

    @Test void depositReturnNotifiesVendorAfterSuccessfulCashRegistration() {
        when(jwtService.extractTokenFromAuthorizationHeader("Bearer token")).thenReturn("token");
        when(jwtService.isTokenValid("token")).thenReturn(true);
        when(jwtService.getEmail("token")).thenReturn("organizer@test.com");
        when(repository.findOrganizerAccountByEmail("organizer@test.com"))
                .thenReturn(Optional.of(Map.of("userId", 7L, "role", "ORGANIZER")));
        Map<String, Object> application = new LinkedHashMap<>();
        application.put("eventOngoing", true);
        application.put("isCancelled", false);
        application.put("reviewStatus", "APPROVED");
        application.put("paymentStatus", "PAID");
        application.put("depositAmount", new BigDecimal("1000"));
        application.put("depositStatus", "NOT_RETURNED");
        application.put("applicationDateCount", 2L);
        application.put("selectedStallCount", 2L);
        application.put("vendorUserId", 18L);
        application.put("eventTitle", "現金退還市集");
        application.put("applicationNo", "APP-18");
        application.put("eventId", 28L);
        when(repository.findDepositRefundCandidate(7L, 18L)).thenReturn(Optional.of(application));
        when(repository.markDepositReturned(7L, 18L)).thenReturn(1);

        var response = service.refundOrganizerDeposit("Bearer token", 18L);

        assertThat(response.isSuccessStatus()).isTrue();
        verify(notificationService).notifyDepositReturned(18L, 18L, "現金退還市集");
    }
}
