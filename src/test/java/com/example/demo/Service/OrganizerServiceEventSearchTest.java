package com.example.demo.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.demo.Repository.OrganizerRepository;

@ExtendWith(MockitoExtension.class)
class OrganizerServiceEventSearchTest {
    private static final String AUTH = "Bearer valid-token";

    @Mock OrganizerRepository organizerRepository;
    @Mock JwtService jwtService;
    @InjectMocks OrganizerService organizerService;

    @BeforeEach
    void authenticate() {
        when(jwtService.extractTokenFromAuthorizationHeader(AUTH)).thenReturn("valid-token");
        when(jwtService.isTokenValid("valid-token")).thenReturn(true);
        when(jwtService.getEmail("valid-token")).thenReturn("organizer@example.com");
        when(organizerRepository.findOrganizerAccountByEmail("organizer@example.com"))
                .thenReturn(Optional.of(new LinkedHashMap<>(Map.of("userId", 7L, "role", "ORGANIZER"))));
    }

    @Test
    void returnsPagedEventStatisticsWithoutTaskSummary() {
        when(organizerRepository.findOrganizerEvents(7L, null, null, null))
                .thenReturn(List.of(eventRow(1L, 100, 100, 96, 88)));

        var response = organizerService.searchOrganizerEvents(
                AUTH, null, null, null, null, "UPCOMING_FIRST", 1, 3);

        assertThat(response.isSuccessStatus()).isTrue();
        assertThat(response.getData().getEvents().getItems()).hasSize(1);
        var event = response.getData().getEvents().getItems().get(0);
        assertThat(event.eventId()).isEqualTo(1L);
        assertThat(event.status()).isEqualTo("full");
        assertThat(event.registeredCount()).isEqualTo(100);
        assertThat(event.pendingReviewCount()).isZero();
        assertThat(event.paidCount()).isEqualTo(96);
        assertThat(event.selectedCount()).isEqualTo(88);
    }

    @Test
    void returnsGlobalTaskSummaryWithApplicationSearch() {
        when(organizerRepository.findOrganizerApplicationTaskSummary(7L)).thenReturn(Map.of(
                "pendingReviewCount", 12,
                "pendingRefundConfirmationCount", 3,
                "pendingStallSelectionCount", 50));
        when(organizerRepository.findOrganizerApplications(7L, null, null, null, null))
                .thenReturn(List.of());

        var response = organizerService.searchOrganizerApplications(
                AUTH, null, null, null, null, null, 1, 6);

        assertThat(response.isSuccessStatus()).isTrue();
        assertThat(response.getData().getTaskSummary().pendingReviewCount()).isEqualTo(12);
        assertThat(response.getData().getTaskSummary().pendingRefundConfirmationCount()).isEqualTo(3);
        assertThat(response.getData().getTaskSummary().pendingStallSelectionCount()).isEqualTo(50);
        assertThat(response.getData().getApplications().getItems()).isEmpty();
    }

    @Test
    void rejectsNonEnumStatusAndInvalidDateRange() {
        assertThat(organizerService.searchOrganizerEvents(
                AUTH, null, "報名中", null, null, "DEFAULT", 1, 6).isSuccessStatus()).isFalse();
        assertThat(organizerService.searchOrganizerEvents(
                AUTH, null, null, java.time.LocalDate.of(2026, 8, 2),
                java.time.LocalDate.of(2026, 8, 1), "DEFAULT", 1, 6).isSuccessStatus()).isFalse();
    }

    private Map<String, Object> eventRow(
            Long id, int capacity, int registered, int paid, int selected) {
        LocalDateTime now = LocalDateTime.now();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("eventId", id);
        row.put("eventTitle", "Test Market");
        row.put("createdAt", now.minusDays(10));
        row.put("eventStartAt", now.plusDays(10));
        row.put("eventEndAt", now.plusDays(11));
        row.put("registrationStartAt", now.minusDays(5));
        row.put("registrationEndAt", now.plusDays(5));
        row.put("workflowStatus", "PUBLISHED");
        row.put("capacity", capacity);
        row.put("registeredCount", registered);
        row.put("pendingReviewCount", 0);
        row.put("paidCount", paid);
        row.put("selectedCount", selected);
        return row;
    }
}
