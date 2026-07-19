package com.example.demo.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
class OrganizerServiceEventPublishTest {
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
    void publishesReadyEventAndKeepsUpcomingStatusAsPublished() {
        Map<String, Object> ready = event("READY_TO_PUBLISH", LocalDateTime.now().plusDays(10),
                LocalDateTime.now().plusDays(5));
        Map<String, Object> published = new LinkedHashMap<>(ready);
        published.put("workflowStatus", "PUBLISHED");
        published.put("publicInfoAt", LocalDateTime.now());
        when(organizerRepository.findOrganizerEventDetail(7L, EVENT_ID))
                .thenReturn(Optional.of(ready), Optional.of(published));
        completePublishData();
        when(organizerRepository.publishOrganizerEvent(any(), any(), any())).thenReturn(1);

        var response = organizerService.publishOrganizerEvent(AUTH, EVENT_ID);

        assertThat(response.isSuccessStatus()).isTrue();
        assertThat(response.getData().workflowStatus()).isEqualTo("PUBLISHED");
        assertThat(response.getData().status()).isEqualTo("published");
        assertThat(response.getData().statusText()).isEqualTo("已發布");
        assertThat(response.getData().availableActions()).containsExactly("REQUEST_UNPUBLISH");
        assertThat(response.getData().publicInfoAt()).isNotNull();
    }

    @Test
    void publishesDuringRegistrationAndShowsRegistrationOpen() {
        Map<String, Object> ready = event("READY_TO_PUBLISH", LocalDateTime.now().plusDays(10),
                LocalDateTime.now().plusDays(5));
        ready.put("registrationStartAt", LocalDateTime.now().minusDays(1));
        Map<String, Object> published = new LinkedHashMap<>(ready);
        published.put("workflowStatus", "PUBLISHED");
        when(organizerRepository.findOrganizerEventDetail(7L, EVENT_ID))
                .thenReturn(Optional.of(ready), Optional.of(published));
        completePublishData();
        when(organizerRepository.publishOrganizerEvent(any(), any(), any())).thenReturn(1);

        var response = organizerService.publishOrganizerEvent(AUTH, EVENT_ID);

        assertThat(response.getData().status()).isEqualTo("registrationOpen");
    }

    @Test
    void rejectsWhenStallCountDoesNotEqualCapacity() {
        when(organizerRepository.findOrganizerEventDetail(7L, EVENT_ID))
                .thenReturn(Optional.of(event("READY_TO_PUBLISH", LocalDateTime.now().plusDays(10),
                        LocalDateTime.now().plusDays(5))));
        when(organizerRepository.findOrganizerEventCategories(EVENT_ID)).thenReturn(List.of(Map.of("categoryId", 1L)));
        when(organizerRepository.findOrganizerEventZones(EVENT_ID)).thenReturn(List.of(Map.of("zoneId", 1L)));
        when(organizerRepository.countEventStalls(EVENT_ID)).thenReturn(9);

        var response = organizerService.publishOrganizerEvent(AUTH, EVENT_ID);

        assertThat(response.getStatusCode()).isEqualTo(400);
        assertThat(response.getData().missingFields()).containsExactly("booth.stalls");
        assertThat(response.getData().expectedStallCount()).isEqualTo(10);
        assertThat(response.getData().actualStallCount()).isEqualTo(9);
        verify(organizerRepository, never()).publishOrganizerEvent(any(), any(), any());
    }

    @Test
    void rejectsWhenRegistrationHasEnded() {
        Map<String, Object> event = event("READY_TO_PUBLISH", LocalDateTime.now().plusDays(10),
                LocalDateTime.now().minusMinutes(1));
        when(organizerRepository.findOrganizerEventDetail(7L, EVENT_ID)).thenReturn(Optional.of(event));
        completePublishData();

        var response = organizerService.publishOrganizerEvent(AUTH, EVENT_ID);

        assertThat(response.getStatusCode()).isEqualTo(400);
        assertThat(response.getData().missingFields()).contains("schedule.registrationEndAt");
    }

    @Test
    void rejectsWhenEventHasStarted() {
        Map<String, Object> event = event("READY_TO_PUBLISH", LocalDateTime.now().minusMinutes(1),
                LocalDateTime.now().plusDays(5));
        event.put("endAt", LocalDateTime.now().plusDays(1));
        when(organizerRepository.findOrganizerEventDetail(7L, EVENT_ID)).thenReturn(Optional.of(event));
        completePublishData();

        var response = organizerService.publishOrganizerEvent(AUTH, EVENT_ID);

        assertThat(response.getStatusCode()).isEqualTo(400);
        assertThat(response.getData().missingFields()).contains("schedule.startAt");
    }

    @Test
    void rejectsEventThatIsNotReadyToPublish() {
        when(organizerRepository.findOrganizerEventDetail(7L, EVENT_ID))
                .thenReturn(Optional.of(event("MAP_BUILDING", LocalDateTime.now().plusDays(10),
                        LocalDateTime.now().plusDays(5))));

        var response = organizerService.publishOrganizerEvent(AUTH, EVENT_ID);

        assertThat(response.getStatusCode()).isEqualTo(409);
        verify(organizerRepository, never()).publishOrganizerEvent(any(), any(), any());
    }

    @Test
    void detectsConcurrentWorkflowChange() {
        when(organizerRepository.findOrganizerEventDetail(7L, EVENT_ID))
                .thenReturn(Optional.of(event("READY_TO_PUBLISH", LocalDateTime.now().plusDays(10),
                        LocalDateTime.now().plusDays(5))));
        completePublishData();
        when(organizerRepository.publishOrganizerEvent(any(), any(), any())).thenReturn(0);

        var response = organizerService.publishOrganizerEvent(AUTH, EVENT_ID);

        assertThat(response.getStatusCode()).isEqualTo(409);
    }

    private void completePublishData() {
        when(organizerRepository.findOrganizerEventCategories(EVENT_ID)).thenReturn(List.of(Map.of("categoryId", 1L)));
        when(organizerRepository.findOrganizerEventZones(EVENT_ID)).thenReturn(List.of(Map.of("zoneId", 1L)));
        when(organizerRepository.countEventStalls(EVENT_ID)).thenReturn(10);
    }

    private Map<String, Object> event(
            String workflowStatus, LocalDateTime startAt, LocalDateTime registrationEndAt) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventId", EVENT_ID);
        event.put("workflowStatus", workflowStatus);
        event.put("coverImageUrl", "/images/cover.png");
        event.put("mapImageUrl", "/images/map.png");
        event.put("startAt", startAt);
        event.put("endAt", startAt.plusDays(1));
        event.put("registrationStartAt", LocalDateTime.now().plusDays(1));
        event.put("registrationEndAt", registrationEndAt);
        event.put("maxBooths", 10);
        event.put("registeredCount", 2);
        return event;
    }
}
