package com.example.demo.Service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.demo.Repository.RequestLogRepository;
import com.example.demo.Repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class RequestLogServiceTest {

    @Mock
    private RequestLogRepository requestLogRepository;
    @Mock
    private StatusLogService statusLogService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private JwtService jwtService;

    private RequestLogService service;

    @BeforeEach
    void setUp() {
        service = new RequestLogService();
        ReflectionTestUtils.setField(service, "requestLogRepository", requestLogRepository);
        ReflectionTestUtils.setField(service, "statusLogService", statusLogService);
        ReflectionTestUtils.setField(service, "userRepository", userRepository);
        ReflectionTestUtils.setField(service, "jwtService", jwtService);
    }

    @Test
    void recordsSuccessfulMutationAndStatusChangesForAuthenticatedUser() {
        MockHttpServletRequest request = request("POST", "/api/vendor/stall/save");
        request.addHeader("Authorization", "Bearer token");
        when(jwtService.extractTokenFromAuthorizationHeader("Bearer token")).thenReturn("token");
        when(jwtService.getEmail("token")).thenReturn("vendor@example.test");
        when(userRepository.findProfileByEmail("vendor@example.test"))
                .thenReturn(Optional.of(Map.of("id", 10L)));
        when(requestLogRepository.createRequestLog(10L, "POST", "/api/vendor/stall/save", 200))
                .thenReturn(99L);

        service.recordRequest(request, 200);

        verify(statusLogService).recordForRequest(99L, request);
    }

    @Test
    void recordsFailedMutationWithoutStatusLog() {
        MockHttpServletRequest request = request("DELETE", "/api/resource/1");
        when(jwtService.extractTokenFromAuthorizationHeader(null)).thenReturn(null);
        when(requestLogRepository.createRequestLog(null, "DELETE", "/api/resource/1", 400)).thenReturn(100L);

        service.recordRequest(request, 400);

        verify(statusLogService, never()).recordForRequest(any(), any());
    }

    @Test
    void ignoresReadOnlyOrNonApiRequests() {
        service.recordRequest(request("GET", "/api/markets/1"), 200);
        service.recordRequest(request("POST", "/health"), 200);

        verify(requestLogRepository, never()).createRequestLog(any(), any(), any(), any());
    }

    @Test
    void loggingFailureDoesNotEscapeIntoApiRequest() {
        MockHttpServletRequest request = request("POST", "/api/resource");
        when(jwtService.extractTokenFromAuthorizationHeader(null)).thenReturn(null);
        when(requestLogRepository.createRequestLog(eq(null), eq("POST"), eq("/api/resource"), eq(200)))
                .thenThrow(new DataAccessResourceFailureException("database unavailable"));

        service.recordRequest(request, 200);

        verify(statusLogService, never()).recordForRequest(any(), any());
    }

    private MockHttpServletRequest request(String method, String path) {
        return new MockHttpServletRequest(method, path);
    }
}
