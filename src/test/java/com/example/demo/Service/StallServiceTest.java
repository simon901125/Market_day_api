package com.example.demo.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.example.demo.Repository.StallRepository;
import com.example.demo.dto.request.StallSelectionRequest;

@ExtendWith(MockitoExtension.class)
class StallServiceTest {
    @Mock StallRepository repository;
    @Mock OrganizerRepository organizerRepository;
    @Mock OrganizerService organizerService;
    @Mock JwtService jwtService;
    @Mock ApplicationStatusService applicationStatusService;
    @Mock TaiwanAddressService addressService;
    StallService service;

    @BeforeEach void setUp() {
        service = new StallService();
        ReflectionTestUtils.setField(service, "stallRepository", repository);
        ReflectionTestUtils.setField(service, "organizerRepository", organizerRepository);
        ReflectionTestUtils.setField(service, "organizerService", organizerService);
        ReflectionTestUtils.setField(service, "jwtService", jwtService);
        ReflectionTestUtils.setField(service, "applicationStatusService", applicationStatusService);
        ReflectionTestUtils.setField(service, "taiwanAddressService", addressService);
    }

    @Test void protectedVendorOperationsRejectMissingAuthorization() {
        StallSelectionRequest selection = new StallSelectionRequest();
        assertThat(service.selectEventStall(null, selection).isSuccessStatus()).isFalse();
        assertThat(service.getVendorAccount(null).isSuccessStatus()).isFalse();
        assertThat(service.loadVendorStallProfile(null).isSuccessStatus()).isFalse();
        assertThat(service.getVendorStallMap(null, "APP-1", null).isSuccessStatus()).isFalse();
        assertThat(service.searchVendorApplications(null, null, null, null, null, 1, 20).isSuccessStatus()).isFalse();
        assertThat(service.getVendorApplicationDetail(null, 1L).isSuccessStatus()).isFalse();
    }

    @Test void saveRejectsMissingBodyBeforeAuthentication() {
        assertThat(service.saveVendorStallProfile("Bearer token", null).isSuccessStatus()).isFalse();
    }

    @Test void selectionRequiresPublishedWorkflow() {
        authenticateVendor(9L);
        when(repository.findApplicationForSelection("APP-1")).thenReturn(Optional.of(selectionApplication(
                "FINAL_REVIEW", true)));

        var response = service.selectEventStall("Bearer token", selectionRequest());

        assertThat(response.isSuccessStatus()).isFalse();
        assertThat(response.getMessage()).isEqualTo("\u6d3b\u52d5\u6d41\u7a0b\u5c1a\u672a\u958b\u653e\u9078\u4f4d");
    }

    @Test void selectionRequiresPublicSelectionTimeToHaveStarted() {
        authenticateVendor(9L);
        when(repository.findApplicationForSelection("APP-1")).thenReturn(Optional.of(selectionApplication(
                "PUBLISHED", false)));

        var response = service.selectEventStall("Bearer token", selectionRequest());

        assertThat(response.isSuccessStatus()).isFalse();
        assertThat(response.getMessage()).isEqualTo("\u5c1a\u672a\u5230\u9078\u4f4d\u8cc7\u8a0a\u516c\u958b\u6642\u9593");
    }

    @Test void publicStatusValidatesEventAndDateRange() {
        assertThat(service.getPublicEventStallsStatus(null, null).isSuccessStatus()).isFalse();
        when(repository.findEventForStallStatus(1L)).thenReturn(Optional.empty());
        assertThat(service.getPublicEventStallsStatus(1L, null).isSuccessStatus()).isFalse();
        when(repository.findEventForStallStatus(2L)).thenReturn(Optional.of(Map.of(
                "startAt", LocalDate.of(2026, 8, 1), "endAt", LocalDate.of(2026, 8, 2))));
        assertThat(service.getPublicEventStallsStatus(2L, LocalDate.of(2026, 8, 3)).isSuccessStatus()).isFalse();
    }

    @Test void publicStatusDefaultsToStartDateAndMapsRows() {
        LocalDate start = LocalDate.of(2026, 8, 1);
        when(repository.findEventForStallStatus(3L)).thenReturn(Optional.of(Map.of("startAt", start, "endAt", start)));
        when(repository.findEventStallsStatus(3L, start)).thenReturn(List.of(Map.of("stallNo", "A01", "status", "AVAILABLE")));
        var response = service.getPublicEventStallsStatus(3L, null);
        assertThat(response.isSuccessStatus()).isTrue();
        assertThat(response.getData()).hasSize(1);
    }

    @Test void marketSearchMapsCapacityToOpenAndFullAndFiltersAfterAvailabilityIsLoaded() {
        when(repository.findMarkets(null, null, null, "FULL", null, null)).thenReturn(List.of(
                Map.of("eventId", 1L, "registrationStatus", "OPEN"),
                Map.of("eventId", 2L, "registrationStatus", "OPEN")));
        when(repository.findEventCategoriesByEventIds(List.of(1L, 2L))).thenReturn(List.of());
        when(repository.findMarketDailyAvailabilities(List.of(1L, 2L))).thenReturn(List.of(
                Map.of("eventId", 1L, "applyDate", LocalDate.of(2026, 8, 1),
                        "totalStalls", 10L, "remainingStalls", 2L),
                Map.of("eventId", 2L, "applyDate", LocalDate.of(2026, 8, 1),
                        "totalStalls", 10L, "remainingStalls", 0L)));

        var response = service.searchMarkets(null, null, null, "FULL", null, null, 1, 6);

        assertThat(response.isSuccessStatus()).isTrue();
        assertThat(response.getData().getMarkets().getTotalItems()).isOne();
        assertThat(response.getData().getMarkets().getItems().getFirst().getValues())
                .containsEntry("eventId", 2L)
                .containsEntry("registrationStatus", "FULL");
    }

    @Test void marketSearchOpenFilterExcludesFullyBookedMarkets() {
        when(repository.findMarkets(null, null, null, "OPEN", null, null)).thenReturn(List.of(
                Map.of("eventId", 1L, "registrationStatus", "OPEN"),
                Map.of("eventId", 2L, "registrationStatus", "OPEN")));
        when(repository.findEventCategoriesByEventIds(List.of(1L, 2L))).thenReturn(List.of());
        when(repository.findMarketDailyAvailabilities(List.of(1L, 2L))).thenReturn(List.of(
                Map.of("eventId", 1L, "applyDate", LocalDate.of(2026, 8, 1),
                        "totalStalls", 10L, "remainingStalls", 2L),
                Map.of("eventId", 2L, "applyDate", LocalDate.of(2026, 8, 1),
                        "totalStalls", 10L, "remainingStalls", 0L)));

        var response = service.searchMarkets(null, null, null, "OPEN", null, null, 1, 6);

        assertThat(response.getData().getMarkets().getTotalItems()).isOne();
        assertThat(response.getData().getMarkets().getItems().getFirst().getValues())
                .containsEntry("eventId", 1L)
                .containsEntry("registrationStatus", "OPEN");
    }

    @Test void organizerMapOperationsRejectMissingAuthorization() {
        assertThat(service.getOrganizerStallMap(null, 1L, null, null, null).isSuccessStatus()).isFalse();
        assertThat(service.getOrganizerStallMapDetail(null, 1L, "A01", null).isSuccessStatus()).isFalse();
    }

    @Test void vendorApplicationSearchFiltersMapsAndPaginatesCurrentVendorRecords() {
        authenticateVendor(9L);
        Map<String, Object> newest = application(2L, "APP-002", "夏日市集");
        Map<String, Object> older = application(1L, "APP-001", "春日市集");
        when(repository.findVendorApplications(eq(9L), eq("市集"), any(), any()))
                .thenReturn(List.of(newest, older));
        when(applicationStatusService.resolveApplicationStatus(anyMap()))
                .thenReturn("待審核", "已付款");

        var response = service.searchVendorApplications(
                "Bearer token", "市集", null,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), 1, 1);

        assertThat(response.isSuccessStatus()).isTrue();
        assertThat(response.getData().getTotalCount()).isEqualTo(2);
        assertThat(response.getData().getApplications().getItems()).hasSize(1);
        assertThat(response.getData().getApplications().getItems().getFirst().getValues())
                .containsEntry("applicationNo", "APP-002")
                .containsEntry("eventTitle", "夏日市集")
                .containsEntry("applicationStatus", "待審核");
        verify(repository).findVendorApplications(
                9L,
                "市集",
                LocalDateTime.of(2026, 8, 1, 0, 0),
                LocalDateTime.of(2026, 9, 1, 0, 0));
    }

    @Test void vendorApplicationSearchCanFilterByResolvedStatus() {
        authenticateVendor(9L);
        Map<String, Object> pending = application(2L, "APP-002", "夏日市集");
        Map<String, Object> paid = application(1L, "APP-001", "春日市集");
        when(repository.findVendorApplications(9L, null, null, null)).thenReturn(List.of(pending, paid));
        when(applicationStatusService.resolveApplicationStatus(anyMap()))
                .thenReturn("待審核", "已付款");

        var response = service.searchVendorApplications(
                "Bearer token", null, "已付款", null, null, 1, 20);

        assertThat(response.getData().getTotalCount()).isEqualTo(1);
        assertThat(response.getData().getApplications().getItems().getFirst().getValues())
                .containsEntry("applicationNo", "APP-001");
    }

    @Test void vendorApplicationDetailRejectsAnotherVendorsApplication() {
        authenticateVendor(9L);
        when(organizerRepository.findVendorApplicationDetail(9L, 8L)).thenReturn(Optional.empty());

        var response = service.getVendorApplicationDetail("Bearer token", 8L);

        assertThat(response.isSuccessStatus()).isFalse();
        verify(organizerRepository).findVendorApplicationDetail(9L, 8L);
    }

    @Test void vendorApplicationDetailUsesSharedDetailSections() {
        authenticateVendor(9L);
        Map<String, Object> application = application(8L, "APP-008", "Integration Market");
        Map<String, Object> sections = Map.of(
                "application", Map.of("applicationNo", "APP-008"),
                "event", Map.of("eventTitle", "Integration Market"),
                "status", List.of());
        when(organizerRepository.findVendorApplicationDetail(9L, 8L))
                .thenReturn(Optional.of(application));
        when(organizerService.buildApplicationDetailResponse(8L, application)).thenReturn(sections);

        var response = service.getVendorApplicationDetail("Bearer token", 8L);

        assertThat(response.isSuccessStatus()).isTrue();
        assertThat(response.getData().getValues()).containsAllEntriesOf(sections);
        verify(organizerService).buildApplicationDetailResponse(8L, application);
    }

    private void authenticateVendor(Long userId) {
        when(jwtService.extractTokenFromAuthorizationHeader("Bearer token")).thenReturn("token");
        when(jwtService.isTokenValid("token")).thenReturn(true);
        when(jwtService.getRole("token")).thenReturn("VENDOR");
        when(jwtService.getEmail("token")).thenReturn("vendor@example.test");
        when(repository.findVendorAccountByEmail("vendor@example.test"))
                .thenReturn(Optional.of(Map.of("userId", userId, "role", "VENDOR")));
    }

    private StallSelectionRequest selectionRequest() {
        StallSelectionRequest.Selection selection = new StallSelectionRequest.Selection();
        selection.setApplyDate(LocalDate.of(2026, 8, 1));
        selection.setStallNo("A01");
        StallSelectionRequest request = new StallSelectionRequest();
        request.setApplicationNo("APP-1");
        request.setSelections(List.of(selection));
        return request;
    }

    private Map<String, Object> selectionApplication(String workflowStatus, boolean selectionOpen) {
        return Map.ofEntries(
                Map.entry("applicationId", 1L),
                Map.entry("applicationNo", "APP-1"),
                Map.entry("eventId", 10L),
                Map.entry("eventTitle", "Selection Event"),
                Map.entry("organizerUserId", 20L),
                Map.entry("brandName", "Test Brand"),
                Map.entry("userId", 9L),
                Map.entry("vendorProfileId", 30L),
                Map.entry("reviewStatus", "APPROVED"),
                Map.entry("paymentStatus", "PAID"),
                Map.entry("isCancelled", false),
                Map.entry("workflowStatus", workflowStatus),
                Map.entry("selectionOpen", selectionOpen));
    }

    private Map<String, Object> application(Long id, String applicationNo, String eventTitle) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("applicationId", id);
        row.put("applicationNo", applicationNo);
        row.put("eventId", id + 10);
        row.put("eventTitle", eventTitle);
        row.put("eventImageUrl", "/images/event.jpg");
        row.put("eventDate", "2026-08-10 - 2026-08-11");
        row.put("eventStartAt", LocalDateTime.of(2026, 8, 10, 10, 0));
        row.put("eventEndAt", LocalDateTime.of(2026, 8, 11, 18, 0));
        row.put("location", "測試會場");
        row.put("appliedAt", LocalDateTime.of(2026, 7, id.intValue(), 10, 0));
        return row;
    }
}
