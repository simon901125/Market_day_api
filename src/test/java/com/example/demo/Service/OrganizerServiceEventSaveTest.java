package com.example.demo.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.demo.Repository.OrganizerRepository;
import com.example.demo.dto.request.OrganizerEventSaveRequest;

@ExtendWith(MockitoExtension.class)
class OrganizerServiceEventSaveTest {
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
    void createsDraftAndReplacesNestedData() {
        OrganizerEventSaveRequest request = validRequest(null);
        when(organizerRepository.countActiveCategories(Set.of(1L))).thenReturn(1);
        when(organizerRepository.createOrganizerEvent(7L, request)).thenReturn(15L);
        stubDetail(15L, "DRAFT");

        var response = organizerService.saveOrganizerEvent(AUTH, request);

        assertThat(response.isSuccessStatus()).isTrue();
        assertThat(response.getData().eventId()).isEqualTo(15L);
        verify(organizerRepository).replaceEventCategories(15L, List.of(1L));
        verify(organizerRepository).replaceEventZones(15L, request.booth().zones());
        verify(organizerRepository).replaceEventEquipment(15L, List.of());
    }

    @Test
    void allowsDraftWithoutTransportation() {
        OrganizerEventSaveRequest request = validRequest(null);
        OrganizerEventSaveRequest.Location location = new OrganizerEventSaveRequest.Location(
                request.location().locationName(), request.location().city(), request.location().district(),
                request.location().address(), null, null, null);
        request = new OrganizerEventSaveRequest(
                request.eventId(), request.eventTitle(), request.summary(), request.description(),
                request.categoryIds(), request.schedule(), location, request.booth(), request.equipment());
        when(organizerRepository.countActiveCategories(Set.of(1L))).thenReturn(1);
        when(organizerRepository.createOrganizerEvent(7L, request)).thenReturn(16L);
        stubDetail(16L, "DRAFT");

        var response = organizerService.saveOrganizerEvent(AUTH, request);

        assertThat(response.isSuccessStatus()).isTrue();
        assertThat(response.getData().eventId()).isEqualTo(16L);
    }

    @Test
    void normalizesSparseDraftWithoutInventingValues() {
        OrganizerEventSaveRequest sparse = new OrganizerEventSaveRequest(
                null, null, null, null, null, null, null, null, null);
        when(organizerRepository.createOrganizerEvent(eq(7L), any(OrganizerEventSaveRequest.class)))
                .thenReturn(17L);
        stubDetail(17L, "DRAFT");

        var response = organizerService.saveOrganizerEvent(AUTH, sparse);

        assertThat(response.isSuccessStatus()).isTrue();
        ArgumentCaptor<OrganizerEventSaveRequest> captor = ArgumentCaptor.forClass(OrganizerEventSaveRequest.class);
        verify(organizerRepository).createOrganizerEvent(eq(7L), captor.capture());
        OrganizerEventSaveRequest saved = captor.getValue();
        assertThat(saved.eventTitle()).isNull();
        assertThat(saved.summary()).isNull();
        assertThat(saved.description()).isNull();
        assertThat(saved.categoryIds()).isEmpty();
        assertThat(saved.schedule().startAt()).isNull();
        assertThat(saved.location().locationName()).isNull();
        assertThat(saved.booth().maxBooths()).isNull();
        assertThat(saved.booth().baseFee()).isNull();
        assertThat(saved.booth().depositAmount()).isNull();
        assertThat(saved.booth().zones()).isEmpty();
        assertThat(saved.equipment().providesEquipmentRental()).isNull();
        assertThat(saved.equipment().providesBasicPower()).isNull();
        assertThat(saved.equipment().allowsExtraPower()).isNull();
        assertThat(saved.equipment().items()).isEmpty();
    }

    @Test
    void rejectsZoneNameOutsideAToZ() {
        OrganizerEventSaveRequest request = withZones(validRequest(null), List.of(
                new OrganizerEventSaveRequest.Zone(null, "1 區", 20, "#F97316")));

        var response = organizerService.saveOrganizerEvent(AUTH, request);

        assertThat(response.getStatusCode()).isEqualTo(400);
        assertThat(response.getMessage()).isEqualTo("攤位分區名稱只能使用 A 區至 Z 區，且不可重複");
    }

    @Test
    void rejectsMoreThanTwentySixZones() {
        List<OrganizerEventSaveRequest.Zone> zones = IntStream.range(0, 27)
                .mapToObj(index -> new OrganizerEventSaveRequest.Zone(
                        null, String.valueOf((char) ('A' + index)) + " 區", 1, "#F97316"))
                .toList();
        OrganizerEventSaveRequest request = withZones(validRequest(null), zones);

        var response = organizerService.saveOrganizerEvent(AUTH, request);

        assertThat(response.getStatusCode()).isEqualTo(400);
        assertThat(response.getMessage()).isEqualTo("攤位分區最多只能有 26 個");
    }

    private OrganizerEventSaveRequest validRequest(Long eventId) {
        LocalDateTime start = LocalDateTime.of(2026, 9, 10, 10, 0);
        return new OrganizerEventSaveRequest(
                eventId,
                "Test Market",
                "Summary",
                "Description",
                List.of(1L),
                new OrganizerEventSaveRequest.Schedule(
                        start, start.plusDays(1), start.minusMonths(2), start.minusDays(1)),
                new OrganizerEventSaveRequest.Location(
                        "Venue", "臺北市", "信義區", "Address", "Metro", null, null),
                new OrganizerEventSaveRequest.Booth(
                        20, BigDecimal.valueOf(3), BigDecimal.valueOf(3),
                        BigDecimal.valueOf(2500), BigDecimal.valueOf(500),
                        List.of(new OrganizerEventSaveRequest.Zone(null, "A 區", 20, "#F97316"))),
                new OrganizerEventSaveRequest.Equipment(false, false, false, List.of()));
    }

    private OrganizerEventSaveRequest withZones(
            OrganizerEventSaveRequest request, List<OrganizerEventSaveRequest.Zone> zones) {
        OrganizerEventSaveRequest.Booth booth = request.booth();
        return new OrganizerEventSaveRequest(
                request.eventId(), request.eventTitle(), request.summary(), request.description(),
                request.categoryIds(), request.schedule(), request.location(),
                new OrganizerEventSaveRequest.Booth(
                        booth.maxBooths(), booth.stallWidth(), booth.stallLength(), booth.baseFee(),
                        booth.depositAmount(), zones),
                request.equipment());
    }

    private void stubDetail(Long eventId, String workflowStatus) {
        LocalDateTime now = LocalDateTime.now();
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventId", eventId);
        event.put("eventTitle", "Test Market");
        event.put("summary", "Summary");
        event.put("description", "Description");
        event.put("startAt", now.plusDays(10));
        event.put("endAt", now.plusDays(11));
        event.put("registrationStartAt", now.minusDays(5));
        event.put("registrationEndAt", now.plusDays(5));
        event.put("locationName", "Venue");
        event.put("city", "臺北市");
        event.put("district", "信義區");
        event.put("address", "Address");
        event.put("maxBooths", 20);
        event.put("registeredCount", 0);
        event.put("baseFee", BigDecimal.valueOf(2500));
        event.put("depositAmount", BigDecimal.valueOf(500));
        event.put("workflowStatus", workflowStatus);
        event.put("createdAt", now);
        when(organizerRepository.findOrganizerEventDetail(7L, eventId)).thenReturn(Optional.of(event));
        when(organizerRepository.findOrganizerEventCategories(eventId)).thenReturn(List.of());
        when(organizerRepository.findOrganizerEventZones(eventId)).thenReturn(List.of());
        when(organizerRepository.findEventEquipments(eventId)).thenReturn(List.of());
    }
}
