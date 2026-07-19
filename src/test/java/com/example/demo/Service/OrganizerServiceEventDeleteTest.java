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
class OrganizerServiceEventDeleteTest {
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
    void cancelsOwnedDraftAndReturnsEventIdentity() {
        when(organizerRepository.findOrganizerEventForDeletion(7L, EVENT_ID))
                .thenReturn(Optional.of(event("DRAFT")));
        when(organizerRepository.cancelDraftOrganizerEvent(7L, EVENT_ID)).thenReturn(1);

        var response = organizerService.deleteOrganizerEvent(AUTH, EVENT_ID);

        assertThat(response.isSuccessStatus()).isTrue();
        assertThat(response.getMessage()).isEqualTo("活動已刪除");
        assertThat(response.getData().eventId()).isEqualTo(EVENT_ID);
        assertThat(response.getData().eventTitle()).isEqualTo("測試活動");
    }

    @Test
    void hidesMissingOtherOwnedAndAlreadyCancelledEvents() {
        when(organizerRepository.findOrganizerEventForDeletion(7L, EVENT_ID))
                .thenReturn(Optional.empty());

        var missing = organizerService.deleteOrganizerEvent(AUTH, EVENT_ID);

        assertThat(missing.getStatusCode()).isEqualTo(404);
        verify(organizerRepository, never()).cancelDraftOrganizerEvent(7L, EVENT_ID);
    }

    @Test
    void treatsAlreadyCancelledEventAsNotFound() {
        when(organizerRepository.findOrganizerEventForDeletion(7L, EVENT_ID))
                .thenReturn(Optional.of(event("CANCELLED")));

        var response = organizerService.deleteOrganizerEvent(AUTH, EVENT_ID);

        assertThat(response.getStatusCode()).isEqualTo(404);
        verify(organizerRepository, never()).cancelDraftOrganizerEvent(7L, EVENT_ID);
    }

    @Test
    void rejectsNonDraftEvent() {
        when(organizerRepository.findOrganizerEventForDeletion(7L, EVENT_ID))
                .thenReturn(Optional.of(event("PENDING_REVIEW")));

        var response = organizerService.deleteOrganizerEvent(AUTH, EVENT_ID);

        assertThat(response.getStatusCode()).isEqualTo(409);
        assertThat(response.getMessageDetails()).isEqualTo("只有草稿活動可以刪除");
        verify(organizerRepository, never()).cancelDraftOrganizerEvent(7L, EVENT_ID);
    }

    @Test
    void detectsConcurrentWorkflowChange() {
        when(organizerRepository.findOrganizerEventForDeletion(7L, EVENT_ID))
                .thenReturn(Optional.of(event("DRAFT")));
        when(organizerRepository.cancelDraftOrganizerEvent(7L, EVENT_ID)).thenReturn(0);

        var response = organizerService.deleteOrganizerEvent(AUTH, EVENT_ID);

        assertThat(response.getStatusCode()).isEqualTo(409);
        assertThat(response.getMessage()).isEqualTo("活動狀態已變更");
    }

    private Map<String, Object> event(String workflowStatus) {
        return new LinkedHashMap<>(Map.of(
                "eventId", EVENT_ID,
                "eventTitle", "測試活動",
                "workflowStatus", workflowStatus));
    }
}
