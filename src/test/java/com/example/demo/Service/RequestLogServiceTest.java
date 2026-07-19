package com.example.demo.Service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
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
import org.springframework.web.util.ContentCachingRequestWrapper;

import com.example.demo.Repository.RequestLogRepository;
import com.example.demo.Repository.UserRepository;
import com.example.demo.dto.response.ApiResponse;
import com.example.demo.dto.response.LoginResponse;
import com.example.demo.dto.response.LoginUserResponse;
import com.fasterxml.jackson.databind.ObjectMapper;

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

    private ObjectMapper objectMapper;
    private RequestLogService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new RequestLogService();
        ReflectionTestUtils.setField(service, "requestLogRepository", requestLogRepository);
        ReflectionTestUtils.setField(service, "statusLogService", statusLogService);
        ReflectionTestUtils.setField(service, "userRepository", userRepository);
        ReflectionTestUtils.setField(service, "jwtService", jwtService);
        ReflectionTestUtils.setField(service, "objectMapper", objectMapper);
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
    void recordsSuccessfulLoginWithUserFromApiResponseWhenAuthorizationHeaderIsAbsent() {
        MockHttpServletRequest request = request("POST", "/api/admin/local-login");
        request.setAttribute(
                RequestLogService.API_RESPONSE_ATTRIBUTE,
                ApiResponse.success(
                        "Login successful",
                        new LoginResponse(
                                "issued-token",
                                new LoginUserResponse(
                                        "admin@example.test",
                                        "Admin",
                                        "ADMIN",
                                        "ACTIVE",
                                        true))));
        when(jwtService.extractTokenFromAuthorizationHeader(null)).thenReturn(null);
        when(userRepository.findProfileByEmail("admin@example.test"))
                .thenReturn(Optional.of(Map.of("id", 20L)));
        when(requestLogRepository.createRequestLog(20L, "POST", "/api/admin/local-login", 200))
                .thenReturn(101L);

        service.recordRequest(request, 200);

        verify(statusLogService).recordForRequest(101L, request);
    }

    @Test
    void recordsFailedLoginWithoutUserIdWhenApiResponseHasNoLoginData() {
        MockHttpServletRequest request = request("POST", "/api/admin/local-login");
        request.setAttribute(RequestLogService.API_RESPONSE_ATTRIBUTE, ApiResponse.fail("Invalid email or password"));
        when(jwtService.extractTokenFromAuthorizationHeader(null)).thenReturn(null);
        when(requestLogRepository.createRequestLog(null, "POST", "/api/admin/local-login", 400))
                .thenReturn(102L);

        service.recordRequest(request, 400);

        verify(statusLogService, never()).recordForRequest(any(), any());
    }

    @Test
    void recordsPublicEmailRequestWithUserFromRequestBody() throws Exception {
        ContentCachingRequestWrapper request = jsonRequest(
                "POST",
                "/api/auth/createAccount/emailVerify",
                """
                        {
                          "email": "vendor@example.test",
                          "code": "123456"
                        }
                        """);
        when(jwtService.extractTokenFromAuthorizationHeader(null)).thenReturn(null);
        when(userRepository.findProfileByEmail("vendor@example.test"))
                .thenReturn(Optional.of(Map.of("id", 30L)));
        when(requestLogRepository.createRequestLog(30L, "POST", "/api/auth/createAccount/emailVerify", 200))
                .thenReturn(103L);

        service.recordRequest(request, 200);

        verify(statusLogService).recordForRequest(103L, request);
    }

    @Test
    void recordsPublicPasswordResetWithUserFromResetToken() throws Exception {
        ContentCachingRequestWrapper request = jsonRequest(
                "POST",
                "/api/auth/resetPassword/reset",
                """
                        {
                          "resetToken": "reset-token",
                          "password": "NewPassword123"
                        }
                        """);
        when(jwtService.extractTokenFromAuthorizationHeader(null)).thenReturn(null);
        when(userRepository.findUserToken(anyString(), eq(UserRepository.TOKEN_TYPE_PASSWORD_RESET)))
                .thenReturn(Optional.of(Map.of("user_id", 40L)));
        when(requestLogRepository.createRequestLog(40L, "POST", "/api/auth/resetPassword/reset", 200))
                .thenReturn(104L);

        service.recordRequest(request, 200);

        verify(statusLogService).recordForRequest(104L, request);
    }

    @Test
    void recordsPublicPasswordResetWithResolvedUserAttributeAfterResetTokenIsConsumed() throws Exception {
        ContentCachingRequestWrapper request = jsonRequest(
                "POST",
                "/api/auth/resetPassword/reset",
                """
                        {
                          "resetToken": "consumed-reset-token",
                          "password": "NewPassword123"
                        }
                        """);
        request.setAttribute(RequestLogService.RESOLVED_USER_ID_ATTRIBUTE, 41L);
        when(jwtService.extractTokenFromAuthorizationHeader(null)).thenReturn(null);
        when(requestLogRepository.createRequestLog(41L, "POST", "/api/auth/resetPassword/reset", 200))
                .thenReturn(106L);

        service.recordRequest(request, 200);

        verify(statusLogService).recordForRequest(106L, request);
    }

    @Test
    void resolvedUserAttributeAvoidsResetTokenLookupAfterTokenWasConsumed() throws Exception {
        ContentCachingRequestWrapper request = jsonRequest(
                "POST",
                "/api/auth/resetPassword/reset",
                """
                        {
                          "resetToken": "consumed-reset-token",
                          "password": "NewPassword123"
                        }
                        """);
        request.setAttribute(RequestLogService.RESOLVED_USER_ID_ATTRIBUTE, 42L);
        when(jwtService.extractTokenFromAuthorizationHeader(null)).thenReturn(null);
        when(requestLogRepository.createRequestLog(42L, "POST", "/api/auth/resetPassword/reset", 200))
                .thenReturn(107L);

        service.recordRequest(request, 200);

        verify(userRepository, never()).findUserToken(anyString(), eq(UserRepository.TOKEN_TYPE_PASSWORD_RESET));
    }


    @Test
    void recordsSuccessfulGoogleRegistrationWithUserFromCredentialEmail() throws Exception {
        String credential = googleCredential("google-user@example.test");
        ContentCachingRequestWrapper request = jsonRequest(
                "POST",
                "/api/vendor/google-register",
                """
                        {
                          "credential": "%s"
                        }
                        """.formatted(credential));
        request.setAttribute(RequestLogService.API_RESPONSE_ATTRIBUTE, ApiResponse.success("Google user registered"));
        when(jwtService.extractTokenFromAuthorizationHeader(null)).thenReturn(null);
        when(userRepository.findProfileByEmail("google-user@example.test"))
                .thenReturn(Optional.of(Map.of("id", 50L)));
        when(requestLogRepository.createRequestLog(50L, "POST", "/api/vendor/google-register", 200))
                .thenReturn(105L);

        service.recordRequest(request, 200);

        verify(statusLogService).recordForRequest(105L, request);
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

    private ContentCachingRequestWrapper jsonRequest(String method, String path, String json) throws Exception {
        MockHttpServletRequest request = request(method, path);
        request.setContentType("application/json");
        request.setCharacterEncoding("UTF-8");
        request.setContent(json.getBytes(StandardCharsets.UTF_8));
        ContentCachingRequestWrapper wrapper = new ContentCachingRequestWrapper(request);
        wrapper.getInputStream().readAllBytes();
        return wrapper;
    }

    private String googleCredential(String email) {
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        String header = encoder.encodeToString("{}".getBytes(StandardCharsets.UTF_8));
        String payload = encoder.encodeToString(("{\"email\":\"" + email + "\"}").getBytes(StandardCharsets.UTF_8));
        return header + "." + payload + ".signature";
    }
}
