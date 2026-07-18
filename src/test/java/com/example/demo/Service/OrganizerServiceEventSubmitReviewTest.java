package com.example.demo.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.demo.Repository.OrganizerRepository;

@ExtendWith(MockitoExtension.class)
class OrganizerServiceEventSubmitReviewTest {
    private static final String AUTH = "Bearer valid-token";
    private static final long EVENT_ID = 21L;

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
    void submitsCompleteDraftForReview() {
        stubCompleteEvent("DRAFT");
        when(organizerRepository.submitOrganizerEventReview(7L, EVENT_ID)).thenReturn(1);

        var response = organizerService.submitOrganizerEventReview(AUTH, EVENT_ID);

        assertThat(response.isSuccessStatus()).isTrue();
        assertThat(response.getData().workflowStatus()).isEqualTo("PENDING_REVIEW");
        assertThat(response.getData().missingFields()).isEmpty();
        verify(organizerRepository).submitOrganizerEventReview(7L, EVENT_ID);
    }

    @Test
    void resubmitsCompleteRevisionRequiredEvent() {
        stubCompleteEvent("REVISION_REQUIRED");
        when(organizerRepository.submitOrganizerEventReview(7L, EVENT_ID)).thenReturn(1);

        var response = organizerService.submitOrganizerEventReview(AUTH, EVENT_ID);

        assertThat(response.isSuccessStatus()).isTrue();
        assertThat(response.getData().workflowStatus()).isEqualTo("PENDING_REVIEW");
    }

    @Test
    void reportsEveryMissingTransportationField() {
        Map<String, Object> event = completeEvent("DRAFT");
        event.put("trafficInfoMetro", null);
        event.put("trafficInfoBus", null);
        event.put("trafficInfoDriving", null);
        stubEventData(event);

        var response = organizerService.submitOrganizerEventReview(AUTH, EVENT_ID);

        assertThat(response.getStatusCode()).isEqualTo(400);
        assertThat(response.getData().missingFields()).containsExactly(
                "location.trafficInfoMetro",
                "location.trafficInfoBus",
                "location.trafficInfoDriving");
        verify(organizerRepository, never()).submitOrganizerEventReview(7L, EVENT_ID);
    }

    @Test
    void rejectsEventOutsideDraftAndRevisionStates() {
        when(organizerRepository.findOrganizerEventDetail(7L, EVENT_ID))
                .thenReturn(Optional.of(completeEvent("PENDING_REVIEW")));

        var response = organizerService.submitOrganizerEventReview(AUTH, EVENT_ID);

        assertThat(response.getStatusCode()).isEqualTo(409);
        verify(organizerRepository, never()).submitOrganizerEventReview(7L, EVENT_ID);
    }

    private void stubCompleteEvent(String workflowStatus) {
        stubEventData(completeEvent(workflowStatus));
    }

    private void stubEventData(Map<String, Object> event) {
        when(organizerRepository.findOrganizerEventDetail(7L, EVENT_ID)).thenReturn(Optional.of(event));
        when(organizerRepository.findOrganizerEventCategories(EVENT_ID))
                .thenReturn(List.of(Map.of("categoryId", 1L)));
        when(organizerRepository.countActiveCategories(Set.of(1L))).thenReturn(1);
        when(organizerRepository.findOrganizerEventZones(EVENT_ID)).thenReturn(List.of(Map.of(
                "zoneName", "A 區",
                "stallCount", 20,
                "colorCode", "#F97316")));
        when(organizerRepository.findEventEquipments(EVENT_ID)).thenReturn(List.of());
    }

    private Map<String, Object> completeEvent(String workflowStatus) {
        LocalDateTime now = LocalDateTime.now();
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventId", EVENT_ID);
        event.put("eventTitle", "Test Market");
        event.put("summary", "Summary");
        event.put("description", "Description");
        event.put("coverImageUrl", "/uploads/event-cover.png");
        event.put("startAt", now.plusDays(30));
        event.put("endAt", now.plusDays(31));
        event.put("registrationStartAt", now.plusDays(2));
        event.put("registrationEndAt", now.plusDays(20));
        event.put("locationName", "Venue");
        event.put("city", "臺北市");
        event.put("district", "信義區");
        event.put("address", "Address");
        event.put("trafficInfoMetro", "無");
        event.put("trafficInfoBus", "Bus");
        event.put("trafficInfoDriving", "Driving");
        event.put("maxBooths", 20);
        event.put("stallWidth", BigDecimal.valueOf(3));
        event.put("stallLength", BigDecimal.valueOf(3));
        event.put("baseFee", BigDecimal.ZERO);
        event.put("depositAmount", BigDecimal.ZERO);
        event.put("mapImageUrl", "/uploads/event-map.png");
        event.put("providesEquipmentRental", false);
        event.put("providesBasicPower", false);
        event.put("allowsExtraPower", false);
        event.put("workflowStatus", workflowStatus);
        event.put("registeredCount", 0);
        return event;
    }
}
