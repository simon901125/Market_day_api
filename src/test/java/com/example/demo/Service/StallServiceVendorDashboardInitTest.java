package com.example.demo.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.demo.Repository.StallRepository;
import com.example.demo.dto.response.ApiResponse;
import com.example.demo.dto.response.VendorDashboardInitResponse;

class StallServiceVendorDashboardInitTest {

    private static final String AUTHORIZATION = "Bearer valid-token";
    private static final String TOKEN = "valid-token";
    private static final String EMAIL = "vendor@example.test";

    private StallRepository stallRepository;
    private JwtService jwtService;
    private StallService stallService;

    @BeforeEach
    void setUp() {
        stallRepository = org.mockito.Mockito.mock(StallRepository.class);
        jwtService = org.mockito.Mockito.mock(JwtService.class);
        stallService = new StallService();
        ReflectionTestUtils.setField(stallService, "stallRepository", stallRepository);
        ReflectionTestUtils.setField(stallService, "jwtService", jwtService);

        when(jwtService.extractTokenFromAuthorizationHeader(AUTHORIZATION)).thenReturn(TOKEN);
        when(jwtService.isTokenValid(TOKEN)).thenReturn(true);
        when(jwtService.getRole(TOKEN)).thenReturn("VENDOR");
        when(jwtService.getEmail(TOKEN)).thenReturn(EMAIL);
    }

    @Test
    void requestsProfileSetupWhenVendorProfileDoesNotExist() {
        when(stallRepository.findVendorDashboardStatusByEmail(EMAIL))
                .thenReturn(Optional.of(Map.of("role", "VENDOR", "hasVendorProfile", false)));

        ApiResponse<VendorDashboardInitResponse> response = stallService.initVendorDashboard(AUTHORIZATION);

        assertThat(response.isSuccessStatus()).isTrue();
        assertThat(response.getData().needsProfileSetup()).isTrue();
    }

    @Test
    void skipsProfileSetupWhenVendorProfileExists() {
        when(stallRepository.findVendorDashboardStatusByEmail(EMAIL))
                .thenReturn(Optional.of(Map.of("role", "VENDOR", "hasVendorProfile", true)));

        ApiResponse<VendorDashboardInitResponse> response = stallService.initVendorDashboard(AUTHORIZATION);

        assertThat(response.isSuccessStatus()).isTrue();
        assertThat(response.getData().needsProfileSetup()).isFalse();
    }

    @Test
    void rejectsNonVendorToken() {
        when(jwtService.getRole(TOKEN)).thenReturn("ORGANIZER");

        ApiResponse<VendorDashboardInitResponse> response = stallService.initVendorDashboard(AUTHORIZATION);

        assertThat(response.isSuccessStatus()).isFalse();
        assertThat(response.getStatusCode()).isEqualTo(400);
        assertThat(response.getData()).isNull();
    }
}
