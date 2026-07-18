package com.example.demo.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.util.ContentCachingRequestWrapper;

import com.example.demo.Repository.StallRepository;
import com.example.demo.Repository.StatusLogRepository;
import com.example.demo.Repository.UserRepository;
import com.example.demo.dto.log.StatusLogEntry;
import com.example.demo.dto.response.ApiResponse;
import com.example.demo.dto.response.LoginResponse;
import com.example.demo.dto.response.LoginUserResponse;
import com.example.demo.dto.response.StallSelectionResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

@ExtendWith(MockitoExtension.class)
class StatusLogServiceTest {

    @Mock
    private StatusLogRepository statusLogRepository;

    @Mock
    private StallRepository stallRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtService jwtService;

    private ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @InjectMocks
    private StatusLogService statusLogService;

    @BeforeEach
    void setUp() {
        RequestContextHolder.resetRequestAttributes();
        ReflectionTestUtils.setField(statusLogService, "objectMapper", objectMapper);
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void stallSelectionRecordsApplicationDateSelectedStallId() {
        MockHttpServletRequest request = post("/api/stalls/select");
        setApiResponse(ApiResponse.success("ok", new StallSelectionResponse("MD001", List.of())), request);
        when(stallRepository.findSelectedApplicationDates("MD001"))
                .thenReturn(List.of(
                        Map.of(
                                "applicationDateId", 101L,
                                "selectedStallId", 88L),
                        Map.of(
                                "applicationDateId", 102L,
                                "selectedStallId", 89L)));

        statusLogService.recordForRequest(1L, request);

        List<StatusLogEntry> entries = capturedEntries();
        assertThat(entries).extracting(StatusLogEntry::getTargetType)
                .containsExactly("APPLICATION_DATE", "APPLICATION_DATE");
        assertThat(entries).extracting(StatusLogEntry::getTargetId)
                .containsExactly(101L, 102L);
        assertThat(entries).extracting(StatusLogEntry::getStatusField)
                .containsExactly("application_dates.selected_stall_id", "application_dates.selected_stall_id");
        assertThat(entries).extracting(StatusLogEntry::getNewStatus)
                .containsExactly("88", "89");
    }

    @Test
    void stallSelectionRecordsOnlyRequestedApplicationDate() {
        MockHttpServletRequest request = post("/api/stalls/select");
        ContentCachingRequestWrapper wrapper = cachedJsonRequest(
                request,
                """
                        {
                          "applicationNo": "MD001",
                          "selections": [
                            {
                              "applyDate": "2026-10-11",
                              "stallNo": "A02"
                            }
                          ]
                        }
                        """);
        when(stallRepository.findSelectedApplicationDates("MD001"))
                .thenReturn(List.of(
                        Map.of(
                                "applicationDateId", 101L,
                                "applyDate", "2026-10-10",
                                "selectedStallId", 88L),
                        Map.of(
                                "applicationDateId", 102L,
                                "applyDate", "2026-10-11",
                                "selectedStallId", 89L)));

        statusLogService.recordForRequest(1L, wrapper);

        List<StatusLogEntry> entries = capturedEntries();
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).getTargetId()).isEqualTo(102L);
        assertThat(entries.get(0).getNewStatus()).isEqualTo("89");
    }

    @ParameterizedTest
    @MethodSource("loginApis")
    void loginApisRecordIsLogin(String path) {
        MockHttpServletRequest request = post(path);
        setApiResponse(ApiResponse.success(
                "ok",
                new LoginResponse(
                        "jwt",
                        new LoginUserResponse("vendor@example.test", "Vendor", "VENDOR", "ACTIVE", true))),
                request);
        when(userRepository.findProfileByEmail("vendor@example.test"))
                .thenReturn(Optional.of(Map.of("id", 20L)));

        statusLogService.recordForRequest(2L, request);

        List<StatusLogEntry> entries = capturedEntries();
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).getTargetType()).isEqualTo("USER");
        assertThat(entries.get(0).getTargetId()).isEqualTo(20L);
        assertThat(entries.get(0).getStatusField()).isEqualTo("users.isLogin");
        assertThat(entries.get(0).getNewStatus()).isEqualTo("true");
    }

    @Test
    void logoutRecordsIsLoginZero() {
        MockHttpServletRequest request = post("/api/auth/logout");
        request.addHeader("Authorization", "Bearer token");
        when(jwtService.extractTokenFromAuthorizationHeader("Bearer token")).thenReturn("token");
        when(jwtService.getEmail("token")).thenReturn("vendor@example.test");
        when(userRepository.findProfileByEmail("vendor@example.test"))
                .thenReturn(Optional.of(Map.of("id", 20L)));

        statusLogService.recordForRequest(3L, request);

        List<StatusLogEntry> entries = capturedEntries();
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).getStatusField()).isEqualTo("users.isLogin");
        assertThat(entries.get(0).getNewStatus()).isEqualTo("false");
    }

    @Test
    void deactivateRecordsStatusAndIsLogin() {
        MockHttpServletRequest request = post("/api/account/deactivate");
        request.addHeader("Authorization", "Bearer token");
        when(jwtService.extractTokenFromAuthorizationHeader("Bearer token")).thenReturn("token");
        when(jwtService.getEmail("token")).thenReturn("vendor@example.test");
        when(userRepository.findProfileByEmail("vendor@example.test"))
                .thenReturn(Optional.of(Map.of("id", 20L)));

        statusLogService.recordForRequest(4L, request);

        List<StatusLogEntry> entries = capturedEntries();
        assertThat(entries).extracting(StatusLogEntry::getStatusField)
                .containsExactly("users.status", "users.isLogin");
        assertThat(entries).extracting(StatusLogEntry::getNewStatus)
                .containsExactly("DISABLED", "0");
    }

    @Test
    void emailVerifyRecordsActiveStatus() {
        MockHttpServletRequest request = post("/api/auth/createAccount/emailVerify");
        when(userRepository.findProfileByEmail("vendor@example.test"))
                .thenReturn(Optional.of(Map.of("id", 20L)));

        ContentCachingRequestWrapper wrapper = cachedJsonRequest(
                request,
                """
                        {"email":"vendor@example.test","code":"123456"}
                        """);

        statusLogService.recordForRequest(5L, wrapper);

        List<StatusLogEntry> entries = capturedEntries();
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).getStatusField()).isEqualTo("users.status");
        assertThat(entries.get(0).getNewStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void organizerApplicationApproveRecordsApprovedStatus() {
        MockHttpServletRequest request = post("/api/organizer/applications/10/approve");
        ContentCachingRequestWrapper wrapper = cachedJsonRequest(request, "{}");

        statusLogService.recordForRequest(6L, wrapper);

        List<StatusLogEntry> entries = capturedEntries();
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).getTargetType()).isEqualTo("EVENT_APPLICATION");
        assertThat(entries.get(0).getTargetId()).isEqualTo(10L);
        assertThat(entries.get(0).getStatusField()).isEqualTo("event_applications.review_status");
        assertThat(entries.get(0).getNewStatus()).isEqualTo("APPROVED");
    }

    @Test
    void adminEventApproveRecordsMapBuildingStatus() {
        MockHttpServletRequest request = post("/api/admin/events/30/approve");
        ContentCachingRequestWrapper wrapper = cachedJsonRequest(request, "{}");

        statusLogService.recordForRequest(8L, wrapper);

        List<StatusLogEntry> entries = capturedEntries();
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).getTargetType()).isEqualTo("EVENT");
        assertThat(entries.get(0).getTargetId()).isEqualTo(30L);
        assertThat(entries.get(0).getStatusField()).isEqualTo("workflow_status");
        assertThat(entries.get(0).getNewStatus()).isEqualTo("MAP_BUILDING");
    }

    @Test
    void organizerEventSubmitReviewRecordsPendingReviewStatus() {
        MockHttpServletRequest request = post("/api/organizer/events/21/submit-review");
        ContentCachingRequestWrapper wrapper = cachedJsonRequest(request, "{}");

        statusLogService.recordForRequest(7L, wrapper);

        List<StatusLogEntry> entries = capturedEntries();
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).getTargetType()).isEqualTo("EVENT");
        assertThat(entries.get(0).getTargetId()).isEqualTo(21L);
        assertThat(entries.get(0).getStatusField()).isEqualTo("workflow_status");
        assertThat(entries.get(0).getNewStatus()).isEqualTo("PENDING_REVIEW");
    }

    @Test
    void adminEventRequestRevisionRecordsRevisionRequiredStatus() {
        MockHttpServletRequest request = post("/api/admin/events/30/request-revision");
        ContentCachingRequestWrapper wrapper = cachedJsonRequest(request, "\"缺少營業執照\"");

        statusLogService.recordForRequest(9L, wrapper);

        List<StatusLogEntry> entries = capturedEntries();
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).getTargetType()).isEqualTo("EVENT");
        assertThat(entries.get(0).getTargetId()).isEqualTo(30L);
        assertThat(entries.get(0).getStatusField()).isEqualTo("workflow_status");
        assertThat(entries.get(0).getNewStatus()).isEqualTo("REVISION_REQUIRED");
    }

    @Test
    void adminEventMapCompleteRecordsReadyToPublishStatus() {
        MockHttpServletRequest request = post("/api/admin/events/30/map-complete");
        ContentCachingRequestWrapper wrapper = cachedJsonRequest(request, "{}");

        statusLogService.recordForRequest(10L, wrapper);

        List<StatusLogEntry> entries = capturedEntries();
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).getTargetType()).isEqualTo("EVENT");
        assertThat(entries.get(0).getTargetId()).isEqualTo(30L);
        assertThat(entries.get(0).getStatusField()).isEqualTo("workflow_status");
        assertThat(entries.get(0).getNewStatus()).isEqualTo("READY_TO_PUBLISH");
    }

    @Test
    void adminEventUnpublishConfirmRecordsUnpublishedAndApprovedRequestStatus() {
        MockHttpServletRequest request = post("/api/admin/events/30/unpublish-confirm");
        ContentCachingRequestWrapper wrapper = cachedJsonRequest(request, "\"庫存已清空\"");
        when(statusLogRepository.findLatestApprovedUnpublishRequestId(30L)).thenReturn(77L);

        statusLogService.recordForRequest(11L, wrapper);

        List<StatusLogEntry> entries = capturedEntries();
        assertThat(entries).hasSize(2);
        assertThat(entries.get(0).getTargetType()).isEqualTo("EVENT");
        assertThat(entries.get(0).getTargetId()).isEqualTo(30L);
        assertThat(entries.get(0).getStatusField()).isEqualTo("workflow_status");
        assertThat(entries.get(0).getNewStatus()).isEqualTo("UNPUBLISHED");
        assertThat(entries.get(1).getTargetType()).isEqualTo("EventUnpublishRequest");
        assertThat(entries.get(1).getTargetId()).isEqualTo(77L);
        assertThat(entries.get(1).getStatusField()).isEqualTo("status");
        assertThat(entries.get(1).getNewStatus()).isEqualTo("APPROVED");
    }

    @Test
    void adminEventUnpublishConfirmRecordsOnlyEventStatusWhenRequestIdUnresolved() {
        MockHttpServletRequest request = post("/api/admin/events/30/unpublish-confirm");
        ContentCachingRequestWrapper wrapper = cachedJsonRequest(request, "\"庫存已清空\"");
        when(statusLogRepository.findLatestApprovedUnpublishRequestId(30L)).thenReturn(null);

        statusLogService.recordForRequest(12L, wrapper);

        List<StatusLogEntry> entries = capturedEntries();
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).getTargetType()).isEqualTo("EVENT");
    }

    @Test
    void organizerApplicationRejectRecordsRejectedStatus() {
        MockHttpServletRequest request = post("/api/organizer/applications/10/reject");
        ContentCachingRequestWrapper wrapper = cachedJsonRequest(
                request,
                """
                        {"reviewNote":"資料不完整","reviewNoteDetail":"請補上商品照片"}
                        """);

        statusLogService.recordForRequest(7L, wrapper);

        List<StatusLogEntry> entries = capturedEntries();
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).getTargetType()).isEqualTo("EVENT_APPLICATION");
        assertThat(entries.get(0).getTargetId()).isEqualTo(10L);
        assertThat(entries.get(0).getStatusField()).isEqualTo("event_applications.review_status");
        assertThat(entries.get(0).getNewStatus()).isEqualTo("REJECTED");
    }

    private static Stream<String> loginApis() {
        return Stream.of(
                "/api/vendor/local-login",
                "/api/organizer/local-login",
                "/api/admin/local-login",
                "/api/vendor/google-login",
                "/api/organizer/google-login");
    }

    private MockHttpServletRequest post(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setCharacterEncoding(StandardCharsets.UTF_8.name());
        return request;
    }

    private void setApiResponse(ApiResponse<?> apiResponse, MockHttpServletRequest request) {
        request.setAttribute(RequestLogService.API_RESPONSE_ATTRIBUTE, apiResponse);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private ContentCachingRequestWrapper cachedJsonRequest(MockHttpServletRequest request, String json) {
        request.setContentType("application/json");
        request.setContent(json.getBytes(StandardCharsets.UTF_8));
        ContentCachingRequestWrapper wrapper = new ContentCachingRequestWrapper(request);
        try {
            wrapper.getInputStream().readAllBytes();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to cache request body", exception);
        }
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(wrapper));
        return wrapper;
    }

    private List<StatusLogEntry> capturedEntries() {
        ArgumentCaptor<List<StatusLogEntry>> captor = ArgumentCaptor.forClass(List.class);
        verify(statusLogRepository).createStatusLogs(any(), captor.capture());
        return captor.getValue();
    }
}
