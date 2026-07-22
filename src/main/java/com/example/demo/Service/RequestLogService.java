package com.example.demo.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.web.util.ContentCachingRequestWrapper;

import com.example.demo.Repository.RequestLogRepository;
import com.example.demo.Repository.UserRepository;
import com.example.demo.dto.request.EmailVerificationRequest;
import com.example.demo.dto.request.LocalRegisterRequest;
import com.example.demo.dto.request.LocalLoginRequest;
import com.example.demo.dto.request.RequestPasswordResetRequest;
import com.example.demo.dto.request.ResendRegistrationVerificationRequest;
import com.example.demo.dto.request.ResetPasswordRequest;
import com.example.demo.dto.response.ApiResponse;
import com.example.demo.dto.response.LoginResponse;
import com.example.demo.dto.response.LoginUserResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;

@Service
public class RequestLogService {

    public static final String RESPONSE_STATUS_CODE_ATTRIBUTE = RequestLogService.class.getName() + ".responseStatusCode";
    public static final String API_RESPONSE_ATTRIBUTE = RequestLogService.class.getName() + ".apiResponse";
    public static final String RESOLVED_USER_ID_ATTRIBUTE = RequestLogService.class.getName() + ".resolvedUserId";

    private static final Logger LOGGER = LoggerFactory.getLogger(RequestLogService.class);
    private static final Set<String> MUTATION_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    @Autowired
    private RequestLogRepository requestLogRepository;

    @Autowired
    private StatusLogService statusLogService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    public void recordRequest(HttpServletRequest request, int statusCode) {
        if (!isMutationApiRequest(request)) {
            return;
        }

        try {
            Long requestLogId = requestLogRepository.createRequestLog(
                    resolveUserId(request),
                    request.getMethod(),
                    request.getRequestURI(),
                    statusCode);
            if (isSuccessStatus(statusCode)) {
                statusLogService.recordForRequest(requestLogId, request);
            }
        } catch (DataAccessException exception) {
            LOGGER.warn(
                    "Failed to write request log for {} {}: {}",
                    request.getMethod(),
                    request.getRequestURI(),
                    exception.getMessage());
            LOGGER.debug("Request log persistence failure details", exception);
        }
    }

    private boolean isSuccessStatus(int statusCode) {
        return statusCode >= 200 && statusCode < 300;
    }

    private boolean isMutationApiRequest(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path != null
                && path.startsWith("/api/")
                && MUTATION_METHODS.contains(request.getMethod());
    }

    private Long resolveUserId(HttpServletRequest request) {
        Long authorizationUserId = resolveAuthorizationUserId(request);
        if (authorizationUserId != null) {
            return authorizationUserId;
        }

        Long loginUserId = resolveLoginUserId(request);
        if (loginUserId != null) {
            return loginUserId;
        }

        Long resolvedUserId = resolveUserIdAttribute(request);
        if (resolvedUserId != null) {
            return resolvedUserId;
        }

        return resolvePublicRequestUserId(request);
    }

    private Long resolveUserIdAttribute(HttpServletRequest request) {
        return toLong(request.getAttribute(RESOLVED_USER_ID_ATTRIBUTE));
    }

    private Long resolveAuthorizationUserId(HttpServletRequest request) {
        String token = jwtService.extractTokenFromAuthorizationHeader(request.getHeader("Authorization"));
        if (token == null || token.isBlank()) {
            return null;
        }

        String email;
        try {
            email = jwtService.getEmail(token);
        } catch (RuntimeException exception) {
            return null;
        }
        return findUserIdByEmail(email);
    }

    private Long resolveLoginUserId(HttpServletRequest request) {
        Object value = request.getAttribute(API_RESPONSE_ATTRIBUTE);
        if (!(value instanceof ApiResponse<?> apiResponse) || !apiResponse.isSuccessStatus()) {
            return null;
        }
        if (!(apiResponse.getData() instanceof LoginResponse loginResponse)) {
            return null;
        }

        LoginUserResponse loginUser = loginResponse.getUser();
        if (loginUser == null || loginUser.getEmail() == null || loginUser.getEmail().isBlank()) {
            return null;
        }

        return findUserIdByEmail(loginUser.getEmail());
    }

    private Long resolvePublicRequestUserId(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path == null) {
            return null;
        }

        return switch (path) {
            case "/api/vendor/local-login", "/api/organizer/local-login", "/api/admin/local-login" ->
                    resolveUserIdFromEmail(requestBody(request, LocalLoginRequest.class));
            case "/api/vendor/local-register", "/api/organizer/local-register" ->
                    resolveUserIdFromEmail(requestBody(request, LocalRegisterRequest.class));
            case "/api/auth/createAccount/emailVerify", "/api/auth/resetPassword/emailVerify" ->
                    resolveUserIdFromEmail(requestBody(request, EmailVerificationRequest.class));
            case "/api/auth/createAccount/resend" ->
                    resolveUserIdFromEmail(requestBody(request, ResendRegistrationVerificationRequest.class));
            case "/api/auth/resetPassword/request" ->
                    resolveUserIdFromEmail(requestBody(request, RequestPasswordResetRequest.class));
            case "/api/auth/resetPassword/reset" ->
                    resolveUserIdFromResetToken(requestBody(request, ResetPasswordRequest.class));
            case "/api/vendor/google-register", "/api/organizer/google-register" ->
                    resolveUserIdFromGoogleCredential(request);
            default -> null;
        };
    }

    private Long resolveUserIdFromEmail(LocalRegisterRequest body) {
        return body == null ? null : findUserIdByEmail(body.getEmail());
    }

    private Long resolveUserIdFromEmail(LocalLoginRequest body) {
        return body == null ? null : findUserIdByEmail(body.getEmail());
    }

    private Long resolveUserIdFromEmail(EmailVerificationRequest body) {
        return body == null ? null : findUserIdByEmail(body.getEmail());
    }

    private Long resolveUserIdFromEmail(ResendRegistrationVerificationRequest body) {
        return body == null ? null : findUserIdByEmail(body.getEmail());
    }

    private Long resolveUserIdFromEmail(RequestPasswordResetRequest body) {
        return body == null ? null : findUserIdByEmail(body.getEmail());
    }

    private Long resolveUserIdFromResetToken(ResetPasswordRequest body) {
        if (body == null || body.getResetToken() == null || body.getResetToken().isBlank()) {
            return null;
        }

        return userRepository.findUserToken(
                hashResetToken(body.getResetToken()),
                UserRepository.TOKEN_TYPE_PASSWORD_RESET)
                .map(token -> toLong(token.get("user_id")))
                .orElse(null);
    }

    private Long resolveUserIdFromGoogleCredential(HttpServletRequest request) {
        if (!isSuccessApiResponse(request)) {
            return null;
        }

        String email = extractEmailFromGoogleCredential(request);
        return findUserIdByEmail(email);
    }

    private boolean isSuccessApiResponse(HttpServletRequest request) {
        Object value = request.getAttribute(API_RESPONSE_ATTRIBUTE);
        return value instanceof ApiResponse<?> apiResponse && apiResponse.isSuccessStatus();
    }

    private String extractEmailFromGoogleCredential(HttpServletRequest request) {
        JsonNode body = requestBody(request, JsonNode.class);
        if (body == null || !body.hasNonNull("credential")) {
            return null;
        }

        String credential = body.get("credential").asText();
        String[] parts = credential.split("\\.");
        if (parts.length < 2) {
            return null;
        }

        try {
            JsonNode payload = objectMapper.readTree(Base64.getUrlDecoder().decode(parts[1]));
            return payload.hasNonNull("email") ? payload.get("email").asText() : null;
        } catch (IllegalArgumentException | IOException exception) {
            return null;
        }
    }

    private <T> T requestBody(HttpServletRequest request, Class<T> bodyType) {
        if (!(request instanceof ContentCachingRequestWrapper wrapper)) {
            return null;
        }

        byte[] content = wrapper.getContentAsByteArray();
        if (content.length == 0) {
            return null;
        }

        try {
            return objectMapper.readValue(new String(content, StandardCharsets.UTF_8), bodyType);
        } catch (IOException exception) {
            return null;
        }
    }

    private Long findUserIdByEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }

        Map<String, Object> user = userRepository.findProfileByEmail(email).orElse(null);
        if (user == null || user.get("id") == null) {
            return null;
        }
        return toLong(user.get("id"));
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String string && !string.isBlank()) {
            return Long.valueOf(string);
        }
        return null;
    }

    private String hashResetToken(String resetToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(resetToken.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
