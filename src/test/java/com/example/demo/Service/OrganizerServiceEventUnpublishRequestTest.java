package com.example.demo.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import com.example.demo.dto.request.OrganizerEventUnpublishRequest;

@ExtendWith(MockitoExtension.class)
class OrganizerServiceEventUnpublishRequestTest {
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
    void createsPendingRequestAndChangesWorkflowStatus() {
        when(organizerRepository.findOrganizerEventDetail(7L, EVENT_ID))
                .thenReturn(Optional.of(event("PUBLISHED")));
        when(organizerRepository.requestOrganizerEventUnpublish(7L, EVENT_ID)).thenReturn(1);
        when(organizerRepository.createEventUnpublishRequest(eq(7L), eq(EVENT_ID), eq("場地臨時停用"), any()))
                .thenReturn(33L);

        var response = organizerService.requestOrganizerEventUnpublish(
                AUTH, EVENT_ID, new OrganizerEventUnpublishRequest("  場地臨時停用  "));

        assertThat(response.isSuccessStatus()).isTrue();
        assertThat(response.getData().unpublishRequestId()).isEqualTo(33L);
        assertThat(response.getData().workflowStatus()).isEqualTo("UNPUBLISH_REQUESTED");
        assertThat(response.getData().status()).isEqualTo("pendingUnpublish");
        assertThat(response.getData().reason()).isEqualTo("場地臨時停用");
        assertThat(response.getData().availableActions()).isEmpty();
    }

    @Test
    void rejectsBlankReason() {
        var response = organizerService.requestOrganizerEventUnpublish(
                AUTH, EVENT_ID, new OrganizerEventUnpublishRequest("   "));

        assertThat(response.getStatusCode()).isEqualTo(400);
        verify(organizerRepository, never()).requestOrganizerEventUnpublish(any(), any());
    }

    @Test
    void rejectsReasonLongerThanFiveHundredCharacters() {
        var response = organizerService.requestOrganizerEventUnpublish(
                AUTH, EVENT_ID, new OrganizerEventUnpublishRequest("理".repeat(501)));

        assertThat(response.getStatusCode()).isEqualTo(400);
    }

    @Test
    void rejectsNonPublishedEvent() {
        when(organizerRepository.findOrganizerEventDetail(7L, EVENT_ID))
                .thenReturn(Optional.of(event("UNPUBLISH_REQUESTED")));

        var response = organizerService.requestOrganizerEventUnpublish(
                AUTH, EVENT_ID, new OrganizerEventUnpublishRequest("場地異動"));

        assertThat(response.getStatusCode()).isEqualTo(409);
        verify(organizerRepository, never()).createEventUnpublishRequest(any(), any(), any(), any());
    }

    @Test
    void detectsConcurrentWorkflowChange() {
        when(organizerRepository.findOrganizerEventDetail(7L, EVENT_ID))
                .thenReturn(Optional.of(event("PUBLISHED")));
        when(organizerRepository.requestOrganizerEventUnpublish(7L, EVENT_ID)).thenReturn(0);

        var response = organizerService.requestOrganizerEventUnpublish(
                AUTH, EVENT_ID, new OrganizerEventUnpublishRequest("場地異動"));

        assertThat(response.getStatusCode()).isEqualTo(409);
        verify(organizerRepository, never()).createEventUnpublishRequest(any(), any(), any(), any());
    }

    private Map<String, Object> event(String workflowStatus) {
        return new LinkedHashMap<>(Map.of("eventId", EVENT_ID, "workflowStatus", workflowStatus));
    }
}
