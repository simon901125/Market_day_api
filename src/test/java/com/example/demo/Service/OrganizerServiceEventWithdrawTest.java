package com.example.demo.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
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
class OrganizerServiceEventWithdrawTest {
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
    void withdrawsPendingReviewEventToDraft() {
        when(organizerRepository.findOrganizerEventDetail(7L, EVENT_ID))
                .thenReturn(Optional.of(event("PENDING_REVIEW")));
        when(organizerRepository.withdrawOrganizerEventReview(7L, EVENT_ID)).thenReturn(1);

        var response = organizerService.withdrawOrganizerEventReview(AUTH, EVENT_ID);

        assertThat(response.isSuccessStatus()).isTrue();
        assertThat(response.getData().workflowStatus()).isEqualTo("DRAFT");
        assertThat(response.getData().status()).isEqualTo("draft");
        assertThat(response.getData().availableActions())
                .containsExactly("EDIT", "SUBMIT_REVIEW", "DELETE");
    }

    @Test
    void rejectsEventThatIsNoLongerPendingReview() {
        when(organizerRepository.findOrganizerEventDetail(7L, EVENT_ID))
                .thenReturn(Optional.of(event("MAP_BUILDING")));

        var response = organizerService.withdrawOrganizerEventReview(AUTH, EVENT_ID);

        assertThat(response.getStatusCode()).isEqualTo(409);
        verify(organizerRepository, never()).withdrawOrganizerEventReview(7L, EVENT_ID);
    }

    @Test
    void detectsConcurrentWorkflowChange() {
        when(organizerRepository.findOrganizerEventDetail(7L, EVENT_ID))
                .thenReturn(Optional.of(event("PENDING_REVIEW")));
        when(organizerRepository.withdrawOrganizerEventReview(7L, EVENT_ID)).thenReturn(0);

        var response = organizerService.withdrawOrganizerEventReview(AUTH, EVENT_ID);

        assertThat(response.getStatusCode()).isEqualTo(409);
    }

    @Test
    void doesNotExposeAnotherOrganizersEvent() {
        when(organizerRepository.findOrganizerEventDetail(7L, EVENT_ID)).thenReturn(Optional.empty());

        var response = organizerService.withdrawOrganizerEventReview(AUTH, EVENT_ID);

        assertThat(response.getStatusCode()).isEqualTo(404);
        verify(organizerRepository, never()).withdrawOrganizerEventReview(7L, EVENT_ID);
    }

    private Map<String, Object> event(String workflowStatus) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventId", EVENT_ID);
        event.put("workflowStatus", workflowStatus);
        event.put("reviewNote", "保留原補件原因");
        return event;
    }
}
