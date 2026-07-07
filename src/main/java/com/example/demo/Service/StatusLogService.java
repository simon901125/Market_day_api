package com.example.demo.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.util.ContentCachingRequestWrapper;

import com.example.demo.Repository.StallRepository;
import com.example.demo.Repository.StatusLogRepository;
import com.example.demo.Repository.UserRepository;
import com.example.demo.dto.log.StatusLogEntry;
import com.example.demo.dto.request.EmailVerificationRequest;
import com.example.demo.dto.request.StallSelectionRequest;
import com.example.demo.dto.response.ApiResponse;
import com.example.demo.dto.response.LoginResponse;
import com.example.demo.dto.response.LoginUserResponse;
import com.example.demo.dto.response.StallSelectionResponse;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;

@Service
public class StatusLogService {

    @Autowired
    private StatusLogRepository statusLogRepository;

    @Autowired
    private StallRepository stallRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    private final List<StatusLogApi> statusLogApis = List.of(
            new StatusLogApi(HttpMethod.POST.name(), "/api/stalls/select", this::buildStallSelectionLogs),
            new StatusLogApi(HttpMethod.POST.name(), "/api/vendor/local-login", this::buildLoginLogs),
            new StatusLogApi(HttpMethod.POST.name(), "/api/organizer/local-login", this::buildLoginLogs),
            new StatusLogApi(HttpMethod.POST.name(), "/api/admin/local-login", this::buildLoginLogs),
            new StatusLogApi(HttpMethod.POST.name(), "/api/vendor/google-login", this::buildLoginLogs),
            new StatusLogApi(HttpMethod.POST.name(), "/api/organizer/google-login", this::buildLoginLogs),
            new StatusLogApi(HttpMethod.POST.name(), "/api/auth/logout", this::buildLogoutLogs),
            new StatusLogApi(HttpMethod.POST.name(), "/api/account/deactivate", this::buildDeactivateLogs),
            new StatusLogApi(HttpMethod.POST.name(), "/api/auth/createAccount/emailVerify", this::buildEmailVerifyLogs),
            new StatusLogApi(HttpMethod.POST.name(), "/api/organizer/applications/{id}/approve",
                    (requestLogId, request) -> buildOrganizerApplicationReviewLogs(
                            requestLogId, request, "/approve", "APPROVED")),
            new StatusLogApi(HttpMethod.POST.name(), "/api/organizer/applications/{id}/reject",
                    (requestLogId, request) -> buildOrganizerApplicationReviewLogs(
                            requestLogId, request, "/reject", "REJECTED")));

    public void recordForRequest(Long requestLogId, HttpServletRequest request) {
        if (requestLogId == null || request == null) {
            return;
        }

        List<StatusLogEntry> entries = statusLogApis.stream()
                .filter(api -> api.method().equals(request.getMethod()) && matchesPath(api.path(), request.getRequestURI()))
                .findFirst()
                .map(api -> api.entryBuilder().build(requestLogId, request))
                .orElseGet(List::of);

        if (!entries.isEmpty()) {
            statusLogRepository.createStatusLogs(requestLogId, entries);
        }
    }

    private List<StatusLogEntry> buildStallSelectionLogs(Long requestLogId, HttpServletRequest request) {
        StallSelectionRequest body = requestBody(request, StallSelectionRequest.class);
        if (body == null) {
            StallSelectionResponse response = responseData(request, StallSelectionResponse.class);
            if (response == null) {
                return List.of();
            }
            body = new StallSelectionRequest();
            body.setApplicationNo(response.getApplicationNo());
        }

        Set<LocalDate> requestedDates = body.getSelections() == null ? Set.of() : body.getSelections().stream()
                .filter(selection -> selection != null && selection.getApplyDate() != null)
                .map(StallSelectionRequest.Selection::getApplyDate)
                .collect(Collectors.toSet());

        List<Map<String, Object>> selectedDates = stallRepository.findSelectedApplicationDates(body.getApplicationNo());
        if (!requestedDates.isEmpty()) {
            selectedDates = selectedDates.stream()
                    .filter(selectedDate -> requestedDates.contains(toLocalDate(selectedDate.get("applyDate"))))
                    .toList();
        }
        if (selectedDates.isEmpty()) {
            return List.of();
        }

        List<StatusLogEntry> entries = new ArrayList<>();
        for (Map<String, Object> selectedDate : selectedDates) {
            entries.add(entry(
                    requestLogId,
                    "APPLICATION_DATE",
                    toLong(selectedDate.get("applicationDateId")),
                    "application_dates.selected_stall_id",
                    selectedDate.get("selectedStallId")));
        }
        return validEntries(entries);
    }

    private List<StatusLogEntry> buildLoginLogs(Long requestLogId, HttpServletRequest request) {
        LoginResponse response = responseData(request, LoginResponse.class);
        if (response == null || response.getUser() == null) {
            return List.of();
        }

        LoginUserResponse user = response.getUser();
        Long userId = findUserIdByEmail(user.getEmail());
        return validEntries(List.of(entry(requestLogId, "USER", userId, "users.isLogin", user.getIsLogin())));
    }

    private List<StatusLogEntry> buildLogoutLogs(Long requestLogId, HttpServletRequest request) {
        Long userId = resolveUserIdFromAuthorization(request);
        return validEntries(List.of(entry(requestLogId, "USER", userId, "users.isLogin", "false")));
    }

    private List<StatusLogEntry> buildDeactivateLogs(Long requestLogId, HttpServletRequest request) {
        Long userId = resolveUserIdFromAuthorization(request);
        return validEntries(List.of(
                entry(requestLogId, "USER", userId, "users.status", "DISABLED"),
                entry(requestLogId, "USER", userId, "users.isLogin", "0")));
    }

    private List<StatusLogEntry> buildEmailVerifyLogs(Long requestLogId, HttpServletRequest request) {
        EmailVerificationRequest body = requestBody(request, EmailVerificationRequest.class);
        if (body == null) {
            return List.of();
        }

        Long userId = findUserIdByEmail(body.getEmail());
        return validEntries(List.of(entry(requestLogId, "USER", userId, "users.status", "ACTIVE")));
    }

    private List<StatusLogEntry> buildOrganizerApplicationReviewLogs(
            Long requestLogId,
            HttpServletRequest request,
            String suffix,
            String reviewStatus) {
        Long applicationId = pathId(request.getRequestURI(), "/api/organizer/applications/", suffix);
        return validEntries(List.of(entry(
                requestLogId,
                "EVENT_APPLICATION",
                applicationId,
                "event_applications.review_status",
                reviewStatus)));
    }

    private StatusLogEntry entry(Long requestLogId, String targetType, Long targetId, String statusField, Object newStatus) {
        return new StatusLogEntry(
                requestLogId,
                targetType,
                targetId,
                statusField,
                newStatus == null ? null : newStatus.toString());
    }

    private List<StatusLogEntry> validEntries(List<StatusLogEntry> entries) {
        return entries.stream()
                .filter(entry -> entry.getTargetType() != null
                        && entry.getTargetId() != null
                        && entry.getStatusField() != null
                        && entry.getNewStatus() != null)
                .toList();
    }

    @SuppressWarnings("unchecked")
    private <T> T responseData(HttpServletRequest request, Class<T> dataType) {
        Object value = request.getAttribute(RequestLogService.API_RESPONSE_ATTRIBUTE);
        if (!(value instanceof ApiResponse<?> apiResponse) || apiResponse.getData() == null) {
            return null;
        }

        Object data = apiResponse.getData();
        if (dataType.isInstance(data)) {
            return (T) data;
        }
        return objectMapper.convertValue(data, dataType);
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

    private Long resolveUserIdFromAuthorization(HttpServletRequest request) {
        String token = jwtService.extractTokenFromAuthorizationHeader(request.getHeader("Authorization"));
        if (token == null || token.isBlank()) {
            return null;
        }

        try {
            return findUserIdByEmail(jwtService.getEmail(token));
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private Long findUserIdByEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }

        Map<String, Object> user = userRepository.findProfileByEmail(email).orElse(null);
        return user == null ? null : toLong(user.get("id"));
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

    private LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        if (value instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        String text = value == null ? "" : value.toString().trim();
        if (text.length() >= 10) {
            try {
                return LocalDate.parse(text.substring(0, 10));
            } catch (RuntimeException ignored) {
                return null;
            }
        }
        return null;
    }

    private boolean matchesPath(String pattern, String path) {
        if (pattern.equals(path)) {
            return true;
        }
        if (!pattern.contains("{")) {
            return false;
        }
        String regex = "^" + pattern.replaceAll("\\{[^/]+\\}", "[^/]+") + "$";
        return path.matches(regex);
    }

    private Long pathId(String path, String prefix, String suffix) {
        if (path == null || !path.startsWith(prefix) || !path.endsWith(suffix)) {
            return null;
        }
        String id = path.substring(prefix.length(), path.length() - suffix.length());
        return toLong(id);
    }

    private record StatusLogApi(String method, String path, StatusLogEntryBuilder entryBuilder) {
    }

    @FunctionalInterface
    private interface StatusLogEntryBuilder {
        List<StatusLogEntry> build(Long requestLogId, HttpServletRequest request);
    }
}
