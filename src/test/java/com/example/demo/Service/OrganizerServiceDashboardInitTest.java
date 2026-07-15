package com.example.demo.Service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.demo.Repository.OrganizerRepository;
import com.example.demo.dto.response.ApiResponse;
import com.example.demo.dto.response.OrganizerDashboardInitResponse;

@ExtendWith(MockitoExtension.class)
class OrganizerServiceDashboardInitTest {

    private static final String AUTHORIZATION_HEADER = "Bearer valid-token";
    private static final String TOKEN = "valid-token";
    private static final String EMAIL = "organizer@example.com";

    @Mock
    private OrganizerRepository organizerRepository;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private OrganizerService organizerService;

    @BeforeEach
    void setUpAuthentication() {
        when(jwtService.extractTokenFromAuthorizationHeader(AUTHORIZATION_HEADER)).thenReturn(TOKEN);
        when(jwtService.isTokenValid(TOKEN)).thenReturn(true);
        when(jwtService.getEmail(TOKEN)).thenReturn(EMAIL);
    }

    @Test
    void shouldRequireProfileWhenARequiredFieldIsMissing() {
        Map<String, Object> organizer = completeOrganizer();
        organizer.put("address", null);
        when(organizerRepository.findOrganizerAccountByEmail(EMAIL)).thenReturn(Optional.of(organizer));

        ApiResponse<OrganizerDashboardInitResponse> response =
                organizerService.initOrganizerDashboard(AUTHORIZATION_HEADER);

        assertTrue(response.isSuccessStatus());
        assertTrue(response.getData().needsProfile());
    }

    @Test
    void shouldNotRequireProfileWhenOptionalCompanyFieldsAreMissing() {
        Map<String, Object> organizer = completeOrganizer();
        organizer.put("companyName", null);
        organizer.put("taxId", null);
        when(organizerRepository.findOrganizerAccountByEmail(EMAIL)).thenReturn(Optional.of(organizer));

        ApiResponse<OrganizerDashboardInitResponse> response =
                organizerService.initOrganizerDashboard(AUTHORIZATION_HEADER);

        assertTrue(response.isSuccessStatus());
        assertFalse(response.getData().needsProfile());
    }

    @Test
    void shouldNotRequireProfileWhenAllRequiredFieldsArePresent() {
        when(organizerRepository.findOrganizerAccountByEmail(EMAIL))
                .thenReturn(Optional.of(completeOrganizer()));

        ApiResponse<OrganizerDashboardInitResponse> response =
                organizerService.initOrganizerDashboard(AUTHORIZATION_HEADER);

        assertTrue(response.isSuccessStatus());
        assertFalse(response.getData().needsProfile());
    }

    private Map<String, Object> completeOrganizer() {
        Map<String, Object> organizer = new HashMap<>();
        organizer.put("userId", 1L);
        organizer.put("role", "ORGANIZER");
        organizer.put("organizerName", "Market Day Organizer");
        organizer.put("contactName", "Test User");
        organizer.put("contactPhone", "0912345678");
        organizer.put("contactEmail", "contact@example.com");
        organizer.put("companyName", "Market Day Company");
        organizer.put("taxId", "12345678");
        organizer.put("city", "Taipei");
        organizer.put("district", "Zhongzheng");
        organizer.put("address", "No. 1 Test Road");
        organizer.put("serviceDays", "MON,TUE");
        organizer.put("serviceStartTime", LocalTime.of(9, 0));
        organizer.put("serviceEndTime", LocalTime.of(18, 0));
        return organizer;
    }
}
