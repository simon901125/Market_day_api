package com.example.demo.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
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
class OrganizerServiceEventDetailTest {
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
    void returnsOwnedDraftWithNestedDataAndActions() {
        Map<String, Object> event = baseEvent("DRAFT");
        when(organizerRepository.findOrganizerEventDetail(7L, 15L)).thenReturn(Optional.of(event));
        when(organizerRepository.findOrganizerEventCategories(15L)).thenReturn(List.of(Map.of(
                "categoryId", 2L, "categoryName", "文創手作", "categorySlug", "handmade")));
        when(organizerRepository.findOrganizerEventZones(15L)).thenReturn(List.of(Map.of(
                "zoneId", 3L, "zoneName", "A 區", "stallCount", 20, "colorCode", "#F97316")));
        when(organizerRepository.findEventEquipments(15L)).thenReturn(List.of(Map.ofEntries(
                Map.entry("eventEquipmentId", 4L), Map.entry("equipmentName", "桌子"),
                Map.entry("rentalFee", BigDecimal.ZERO), Map.entry("pricingUnit", "DAY"),
                Map.entry("chargeType", "FREE"), Map.entry("itemType", "EQUIPMENT"),
                Map.entry("rentalStatus", "ACTIVE"))));

        var response = organizerService.getOrganizerEventDetail(AUTH, 15L);

        assertThat(response.isSuccessStatus()).isTrue();
        assertThat(response.getData().categories()).hasSize(1);
        assertThat(response.getData().booth().zones().get(0).colorCode()).isEqualTo("#F97316");
        assertThat(response.getData().equipment().items()).hasSize(1);
        assertThat(response.getData().availableActions())
                .containsExactly("EDIT", "SUBMIT_REVIEW", "DELETE");
    }

    @Test
    void hidesExistenceOfAnotherOrganizersEvent() {
        when(organizerRepository.findOrganizerEventDetail(7L, 99L)).thenReturn(Optional.empty());
        var response = organizerService.getOrganizerEventDetail(AUTH, 99L);
        assertThat(response.getStatusCode()).isEqualTo(404);
        assertThat(response.getData()).isNull();
    }

    private Map<String, Object> baseEvent(String workflowStatus) {
        LocalDateTime now = LocalDateTime.now();
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventId", 15L);
        event.put("eventTitle", "Test Market");
        event.put("summary", "Summary");
        event.put("description", "Description");
        event.put("startAt", now.plusDays(10));
        event.put("endAt", now.plusDays(11));
        event.put("registrationStartAt", now.minusDays(1));
        event.put("registrationEndAt", now.plusDays(5));
        event.put("locationName", "Venue");
        event.put("city", "臺北市");
        event.put("address", "Address");
        event.put("maxBooths", 20);
        event.put("registeredCount", 0);
        event.put("baseFee", BigDecimal.valueOf(2500));
        event.put("depositAmount", BigDecimal.valueOf(1000));
        event.put("workflowStatus", workflowStatus);
        event.put("createdAt", now.minusDays(3));
        return event;
    }
}
