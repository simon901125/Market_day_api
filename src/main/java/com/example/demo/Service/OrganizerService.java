package com.example.demo.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.Repository.OrganizerRepository;
import com.example.demo.dto.request.OrganizerApplicationReviewRequest;
import com.example.demo.dto.request.OrganizerEventSaveRequest;
import com.example.demo.dto.request.OrganizerEventUnpublishRequest;
import com.example.demo.dto.request.OrganizerProfileSaveRequest;
import com.example.demo.dto.response.ApiResponse;
import com.example.demo.dto.response.CategoryResponse;
import com.example.demo.dto.response.MapBackedResponse;
import com.example.demo.dto.response.OrganizerAccountResponse;
import com.example.demo.dto.response.OrganizerApplicationDetailResponse;
import com.example.demo.dto.response.OrganizerApplicationSearchResponse;
import com.example.demo.dto.response.OrganizerApplicationSummaryResponse;
import com.example.demo.dto.response.OrganizerAccountingSearchResponse;
import com.example.demo.dto.response.OrganizerAccountingSummaryResponse;
import com.example.demo.dto.response.OrganizerDashboardInitResponse;
import com.example.demo.dto.response.OrganizerEquipmentSearchResponse;
import com.example.demo.dto.response.OrganizerEquipmentSummaryResponse;
import com.example.demo.dto.response.OrganizerEventSearchResponse;
import com.example.demo.dto.response.OrganizerEventDetailResponse;
import com.example.demo.dto.response.OrganizerEventDeleteResponse;
import com.example.demo.dto.response.OrganizerEventSummaryResponse;
import com.example.demo.dto.response.OrganizerEventSubmitReviewResponse;
import com.example.demo.dto.response.OrganizerPaymentSearchResponse;
import com.example.demo.dto.response.OrganizerPaymentSummaryResponse;
import com.example.demo.dto.response.OrganizerPaymentDetailResponse;
import com.example.demo.dto.response.OrganizerEventWithdrawResponse;
import com.example.demo.dto.response.OrganizerEventPublishResponse;
import com.example.demo.dto.response.OrganizerEventUnpublishRequestResponse;
import com.example.demo.dto.response.OrganizerTaskSummaryResponse;
import com.example.demo.dto.response.OrganizerStallEventSearchResponse;
import com.example.demo.dto.response.PageResponse;
import com.example.demo.dto.response.OrganizerStallEventSummaryResponse;
import com.example.demo.enums.status.EventStatus;
import com.example.demo.enums.status.WorkflowStatus;

@Service
public class OrganizerService {

    public record ReportExport(
            boolean success,
            byte[] content,
            String filename,
            String contentType,
            String errorMessage) {

        public static ReportExport excel(String filename, byte[] content) {
            return new ReportExport(
                    true,
                    content,
                    filename,
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    null);
        }

        public static ReportExport fail(String message) {
            return new ReportExport(false, null, null, "text/plain; charset=UTF-8", message);
        }
    }

    private static final DateTimeFormatter SERVICE_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter SPACE_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DISPLAY_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DISPLAY_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final Pattern VOLTAGE_PATTERN = Pattern.compile("(\\d{2,4})\\s*[vV]");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern TAIWAN_MOBILE_PATTERN = Pattern.compile("^09\\d{8}$");
    private static final Pattern TAX_ID_PATTERN = Pattern.compile("^\\d{8}$");
    private static final Set<String> SERVICE_DAY_CODES = Set.of("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN");
    @Autowired
    private OrganizerRepository organizerRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ApplicationStatusService applicationStatusService;

    @Autowired
    private TaiwanAddressService taiwanAddressService;

    @Autowired
    private NotificationService notificationService;

    public ApiResponse<OrganizerDashboardInitResponse> initOrganizerDashboard(String authorizationHeader) {
        Map<String, Object> organizer = getAuthenticatedOrganizer(authorizationHeader);
        if (organizer.containsKey("message")) {
            return ApiResponse.fail(organizer.get("message").toString());
        }

        boolean needsProfile = isOrganizerProfileIncomplete(organizer);
        return ApiResponse.success(
                "Organizer dashboard initialized successfully",
                new OrganizerDashboardInitResponse(needsProfile));
    }

    public ApiResponse<OrganizerEventSearchResponse> searchOrganizerEvents(
            String authorizationHeader,
            String keyword,
            String status,
            LocalDate startDate,
            LocalDate endDate,
            String sort,
            Integer page,
            Integer pageSize,
            Boolean registrationOverview) {
        Map<String, Object> organizer = getAuthenticatedOrganizer(authorizationHeader);
        if (organizer.containsKey("message")) {
            return ApiResponse.fail(organizer.get("message").toString());
        }
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            return ApiResponse.fail("Invalid date range");
        }

        EventStatus statusFilter = parseEventStatus(status);
        if (normalizeText(status) != null && statusFilter == null) {
            return ApiResponse.fail("Invalid event status");
        }
        String normalizedSort = normalizeText(sort);
        if (normalizedSort == null) {
            normalizedSort = "DEFAULT";
        }
        normalizedSort = normalizedSort.toUpperCase();
        if (!Set.of("DEFAULT", "UPCOMING_FIRST").contains(normalizedSort)) {
            return ApiResponse.fail("Invalid sort option");
        }

        Long organizerUserId = ((Number) organizer.get("userId")).longValue();
        LocalDateTime startAt = startDate == null ? null : startDate.atStartOfDay();
        LocalDateTime endExclusive = endDate == null ? null : endDate.plusDays(1).atStartOfDay();
        List<OrganizerEventSummaryResponse> events = organizerRepository
                .findOrganizerEvents(organizerUserId, keyword, startAt, endExclusive)
                .stream()
                .map(this::toOrganizerEventSummary)
                .filter(event -> statusFilter == null || statusFilter.getStatus().equals(event.status()))
                .filter(event -> !Boolean.TRUE.equals(registrationOverview)
                        || isRegistrationOverviewEvent(event))
                .sorted(organizerEventComparator(normalizedSort))
                .toList();

        return ApiResponse.success(
                "Organizer events retrieved successfully",
                new OrganizerEventSearchResponse(PageResponse.from(events, page, pageSize)));
    }

    private boolean isRegistrationOverviewEvent(OrganizerEventSummaryResponse event) {
        return Set.of(
                WorkflowStatus.PUBLISHED.name(),
                WorkflowStatus.FINAL_REVIEW.name(),
                WorkflowStatus.UNPUBLISH_REQUESTED.name())
                .contains(event.workflowStatus());
    }

    public ApiResponse<OrganizerEventDetailResponse> getOrganizerEventDetail(
            String authorizationHeader, Long eventId) {
        Map<String, Object> organizer = getAuthenticatedOrganizer(authorizationHeader);
        if (organizer.containsKey("message")) {
            return ApiResponse.fail(organizer.get("message").toString());
        }
        if (eventId == null || eventId <= 0) {
            return ApiResponse.fail("Invalid event id");
        }

        Long organizerUserId = ((Number) organizer.get("userId")).longValue();
        Map<String, Object> event = organizerRepository
                .findOrganizerEventDetail(organizerUserId, eventId).orElse(null);
        if (event == null) {
            return ApiResponse.fail(404, "Organizer event not found");
        }

        WorkflowStatus workflowStatus = WorkflowStatus.valueOf(statusText(event.get("workflowStatus")));
        if (workflowStatus == WorkflowStatus.CANCELLED) {
            return ApiResponse.fail(404, "Organizer event not found");
        }
        EventStatus eventStatus = resolveOrganizerEventStatus(
                event, workflowStatus, intValue(event.get("maxBooths")), intValue(event.get("registeredCount")));
        List<OrganizerEventDetailResponse.Category> categories = organizerRepository
                .findOrganizerEventCategories(eventId).stream()
                .map(row -> new OrganizerEventDetailResponse.Category(
                        longValue(row.get("categoryId")), statusText(row.get("categoryName")),
                        statusText(row.get("categorySlug"))))
                .toList();
        List<OrganizerEventDetailResponse.Zone> zones = organizerRepository
                .findOrganizerEventZones(eventId).stream()
                .map(row -> new OrganizerEventDetailResponse.Zone(
                        longValue(row.get("zoneId")), statusText(row.get("zoneName")),
                        intValue(row.get("stallCount")), normalizeText(row.get("colorCode"))))
                .toList();
        List<OrganizerEventDetailResponse.Item> items = organizerRepository.findEventEquipments(eventId).stream()
                .map(this::toOrganizerEventEquipmentItem)
                .toList();

        OrganizerEventDetailResponse detail = new OrganizerEventDetailResponse(
                eventId,
                statusText(event.get("eventTitle")),
                statusText(event.get("summary")),
                statusText(event.get("description")),
                categories,
                normalizeText(event.get("coverImageUrl")),
                new OrganizerEventDetailResponse.Schedule(
                        toLocalDateTime(event.get("startAt")), toLocalDateTime(event.get("endAt")),
                        toLocalDateTime(event.get("registrationStartAt")),
                        toLocalDateTime(event.get("registrationEndAt")),
                        toLocalDateTime(event.get("publicInfoAt")),
                        toLocalDateTime(event.get("brandsPublicAt"))),
                new OrganizerEventDetailResponse.Location(
                        statusText(event.get("locationName")), statusText(event.get("city")),
                        normalizeText(event.get("district")), statusText(event.get("address")),
                        normalizeText(event.get("trafficInfoMetro")), normalizeText(event.get("trafficInfoBus")),
                        normalizeText(event.get("trafficInfoDriving"))),
                new OrganizerEventDetailResponse.Booth(
                        nullableInteger(event.get("maxBooths")), toBigDecimal(event.get("stallWidth")),
                        toBigDecimal(event.get("stallLength")), toBigDecimal(event.get("baseFee")),
                        toBigDecimal(event.get("depositAmount")), normalizeText(event.get("mapImageUrl")), zones),
                new OrganizerEventDetailResponse.Equipment(
                        nullableBoolean(event.get("providesEquipmentRental")),
                        nullableBoolean(event.get("providesBasicPower")),
                        nullableBoolean(event.get("allowsExtraPower")),
                        items),
                workflowStatus.name(), eventStatus.getStatus(), eventStatus.getDescription(),
                normalizeText(event.get("reviewNote")), availableOrganizerEventActions(workflowStatus, eventStatus),
                toLocalDateTime(event.get("createdAt")));
        return ApiResponse.success("Organizer event detail retrieved successfully", detail);
    }

    @Transactional
    public ApiResponse<OrganizerEventDeleteResponse> deleteOrganizerEvent(
            String authorizationHeader, Long eventId) {
        Map<String, Object> organizer = getAuthenticatedOrganizer(authorizationHeader);
        if (organizer.containsKey("message")) {
            return ApiResponse.fail(organizer.get("message").toString());
        }
        if (eventId == null || eventId <= 0) {
            return ApiResponse.fail("Invalid event id");
        }

        Long organizerUserId = ((Number) organizer.get("userId")).longValue();
        Map<String, Object> event = organizerRepository
                .findOrganizerEventForDeletion(organizerUserId, eventId).orElse(null);
        if (event == null || WorkflowStatus.CANCELLED.name().equals(statusText(event.get("workflowStatus")))) {
            return ApiResponse.fail(404, "Organizer event not found");
        }
        if (!WorkflowStatus.DRAFT.name().equals(statusText(event.get("workflowStatus")))) {
            return new ApiResponse<>(
                    409,
                    "目前狀態無法刪除活動",
                    "只有草稿活動可以刪除",
                    null);
        }
        if (organizerRepository.cancelDraftOrganizerEvent(organizerUserId, eventId) != 1) {
            return new ApiResponse<>(
                    409,
                    "活動狀態已變更",
                    "請重新載入活動資料後再試",
                    null);
        }

        notificationService.notifyOrganizerEventCancelled(
                organizerUserId,
                eventId,
                statusText(event.get("eventTitle")));

        return ApiResponse.success(
                "Organizer event deleted successfully",
                new OrganizerEventDeleteResponse(eventId, statusText(event.get("eventTitle"))));
    }

    @Transactional
    public ApiResponse<OrganizerEventDetailResponse> saveOrganizerEvent(
            String authorizationHeader, OrganizerEventSaveRequest request) {
        Map<String, Object> organizer = getAuthenticatedOrganizer(authorizationHeader);
        if (organizer.containsKey("message")) {
            return ApiResponse.fail(organizer.get("message").toString());
        }

        if (request == null) {
            return ApiResponse.fail("Event data is required");
        }
        OrganizerEventSaveRequest draft = normalizeOrganizerEventDraft(request);
        String validationError = validateOrganizerEventDraft(draft);
        if (validationError != null) {
            return ApiResponse.fail(validationError);
        }

        Set<Long> categoryIds = new LinkedHashSet<>(draft.categoryIds());
        if (!categoryIds.isEmpty()
                && organizerRepository.countActiveCategories(categoryIds) != categoryIds.size()) {
            return ApiResponse.fail("Event categories are invalid or inactive");
        }

        Long organizerUserId = ((Number) organizer.get("userId")).longValue();
        Long eventId = draft.eventId();
        if (eventId == null) {
            eventId = organizerRepository.createOrganizerEvent(organizerUserId, draft);
        } else {
            if (eventId <= 0) {
                return ApiResponse.fail("Invalid event id");
            }
            Map<String, Object> existing = organizerRepository
                    .findOrganizerEventDetail(organizerUserId, eventId).orElse(null);
            if (existing == null) {
                return ApiResponse.fail(404, "Organizer event not found");
            }
            WorkflowStatus workflowStatus = WorkflowStatus.valueOf(statusText(existing.get("workflowStatus")));
            if (workflowStatus != WorkflowStatus.DRAFT && workflowStatus != WorkflowStatus.REVISION_REQUIRED) {
                return ApiResponse.fail(409, "Event cannot be edited in its current workflow status");
            }
            if (organizerRepository.updateOrganizerEvent(organizerUserId, draft) != 1) {
                return ApiResponse.fail(409, "Event cannot be edited in its current workflow status");
            }
        }

        organizerRepository.replaceEventCategories(eventId, List.copyOf(categoryIds));
        organizerRepository.replaceEventZones(eventId, draft.booth().zones());
        List<OrganizerEventSaveRequest.Item> equipmentItems = draft.equipment().items();
        organizerRepository.replaceEventEquipment(eventId, equipmentItems);

        ApiResponse<OrganizerEventDetailResponse> detail = getOrganizerEventDetail(authorizationHeader, eventId);
        return new ApiResponse<>(detail.getStatusCode(), "活動儲存成功", detail.getData());
    }

    @Transactional
    public ApiResponse<OrganizerEventSubmitReviewResponse> submitOrganizerEventReview(
            String authorizationHeader, Long eventId) {
        Map<String, Object> organizer = getAuthenticatedOrganizer(authorizationHeader);
        if (organizer.containsKey("message")) {
            return ApiResponse.fail(organizer.get("message").toString());
        }
        if (eventId == null || eventId <= 0) {
            return ApiResponse.fail("Invalid event id");
        }

        Long organizerUserId = ((Number) organizer.get("userId")).longValue();
        Map<String, Object> event = organizerRepository
                .findOrganizerEventDetail(organizerUserId, eventId).orElse(null);
        if (event == null) {
            return ApiResponse.fail(404, "Organizer event not found");
        }

        WorkflowStatus workflowStatus = WorkflowStatus.valueOf(statusText(event.get("workflowStatus")));
        if (workflowStatus != WorkflowStatus.DRAFT && workflowStatus != WorkflowStatus.REVISION_REQUIRED) {
            return ApiResponse.fail(409, "Event cannot be submitted in its current workflow status");
        }

        List<Map<String, Object>> categories = organizerRepository.findOrganizerEventCategories(eventId);
        List<Map<String, Object>> zones = organizerRepository.findOrganizerEventZones(eventId);
        List<Map<String, Object>> equipmentItems = organizerRepository.findEventEquipments(eventId);
        List<String> missingFields = validateOrganizerEventReview(event, categories, zones, equipmentItems);
        if (!missingFields.isEmpty()) {
            EventStatus currentStatus = workflowStatus == WorkflowStatus.DRAFT
                    ? EventStatus.DRAFT : EventStatus.REVISION_REQUIRED;
            OrganizerEventSubmitReviewResponse validation = new OrganizerEventSubmitReviewResponse(
                    eventId,
                    workflowStatus.name(),
                    currentStatus.getStatus(),
                    currentStatus.getDescription(),
                    availableOrganizerEventActions(workflowStatus, currentStatus),
                    missingFields);
            return new ApiResponse<>(400, "活動資料尚未填寫完整", "請完成以下欄位", validation);
        }

        if (organizerRepository.submitOrganizerEventReview(organizerUserId, eventId) != 1) {
            return ApiResponse.fail(409, "Event cannot be submitted in its current workflow status");
        }
        notificationService.notifyAdminsEventSubmitted(
                eventId,
                normalizeText(event.get("eventTitle")),
                normalizeText(organizer.get("organizerName")),
                workflowStatus == WorkflowStatus.REVISION_REQUIRED);

        return ApiResponse.success("Organizer event submitted for review successfully",
                new OrganizerEventSubmitReviewResponse(
                        eventId,
                        WorkflowStatus.PENDING_REVIEW.name(),
                        EventStatus.PENDING_REVIEW.getStatus(),
                        EventStatus.PENDING_REVIEW.getDescription(),
                        List.of(),
                        List.of()));
    }

    @Transactional
    public ApiResponse<OrganizerEventWithdrawResponse> withdrawOrganizerEventReview(
            String authorizationHeader, Long eventId) {
        Map<String, Object> organizer = getAuthenticatedOrganizer(authorizationHeader);
        if (organizer.containsKey("message")) {
            return ApiResponse.fail(organizer.get("message").toString());
        }
        if (eventId == null || eventId <= 0) {
            return ApiResponse.fail("Invalid event id");
        }

        Long organizerUserId = ((Number) organizer.get("userId")).longValue();
        Map<String, Object> event = organizerRepository
                .findOrganizerEventDetail(organizerUserId, eventId).orElse(null);
        if (event == null) {
            return ApiResponse.fail(404, "Organizer event not found");
        }
        if (!WorkflowStatus.PENDING_REVIEW.name().equals(statusText(event.get("workflowStatus")))) {
            return ApiResponse.fail(409, "Event cannot be withdrawn in its current workflow status");
        }
        if (organizerRepository.withdrawOrganizerEventReview(organizerUserId, eventId) != 1) {
            return ApiResponse.fail(409, "Event workflow status changed before withdrawal");
        }
        notificationService.notifyAdminsEventReviewWithdrawn(
                eventId,
                normalizeText(event.get("eventTitle")),
                normalizeText(organizer.get("organizerName")));
        notificationService.notifyOrganizerApplicationResubmitted(
                organizerUserId,
                eventId,
                normalizeText(event.get("eventTitle")));

        return ApiResponse.success("Organizer event review withdrawn successfully",
                new OrganizerEventWithdrawResponse(
                        eventId,
                        WorkflowStatus.DRAFT.name(),
                        EventStatus.DRAFT.getStatus(),
                        EventStatus.DRAFT.getDescription(),
                        availableOrganizerEventActions(WorkflowStatus.DRAFT, EventStatus.DRAFT)));
    }

    @Transactional
    public ApiResponse<OrganizerEventPublishResponse> publishOrganizerEvent(
            String authorizationHeader, Long eventId) {
        Map<String, Object> organizer = getAuthenticatedOrganizer(authorizationHeader);
        if (organizer.containsKey("message")) {
            return ApiResponse.fail(organizer.get("message").toString());
        }
        if (eventId == null || eventId <= 0) {
            return ApiResponse.fail("Invalid event id");
        }

        Long organizerUserId = ((Number) organizer.get("userId")).longValue();
        Map<String, Object> event = organizerRepository
                .findOrganizerEventDetail(organizerUserId, eventId).orElse(null);
        if (event == null) {
            return ApiResponse.fail(404, "Organizer event not found");
        }

        WorkflowStatus workflowStatus = WorkflowStatus.valueOf(statusText(event.get("workflowStatus")));
        if (workflowStatus != WorkflowStatus.READY_TO_PUBLISH) {
            return ApiResponse.fail(409, "Event cannot be published in its current workflow status");
        }

        Integer expectedStallCount = nullableInteger(event.get("maxBooths"));
        int actualStallCount = organizerRepository.countEventStalls(eventId);
        List<String> missingFields = validateOrganizerEventPublish(eventId, event, actualStallCount);
        if (!missingFields.isEmpty()) {
            EventStatus status = EventStatus.READY_TO_PUBLISH;
            OrganizerEventPublishResponse validation = new OrganizerEventPublishResponse(
                    eventId,
                    workflowStatus.name(),
                    status.getStatus(),
                    status.getDescription(),
                    toLocalDateTime(event.get("publicInfoAt")),
                    availableOrganizerEventActions(workflowStatus, status),
                    expectedStallCount,
                    actualStallCount,
                    missingFields);
            return new ApiResponse<>(400, "活動尚未符合發布條件", "請確認活動資料與攤位地圖", validation);
        }

        if (organizerRepository.publishOrganizerEvent(
                organizerUserId, eventId, LocalDateTime.now()) != 1) {
            return ApiResponse.fail(409, "Event workflow status changed before publication");
        }

        Map<String, Object> published = organizerRepository
                .findOrganizerEventDetail(organizerUserId, eventId).orElseThrow();
        EventStatus status = resolveOrganizerEventStatus(
                published,
                WorkflowStatus.PUBLISHED,
                intValue(published.get("maxBooths")),
                intValue(published.get("registeredCount")));
        return ApiResponse.success("Organizer event published successfully",
                new OrganizerEventPublishResponse(
                        eventId,
                        WorkflowStatus.PUBLISHED.name(),
                        status.getStatus(),
                        status.getDescription(),
                        toLocalDateTime(published.get("publicInfoAt")),
                        availableOrganizerEventActions(WorkflowStatus.PUBLISHED, status),
                        nullableInteger(published.get("maxBooths")),
                        organizerRepository.countEventStalls(eventId),
                        List.of()));
    }

    @Transactional
    public ApiResponse<OrganizerEventUnpublishRequestResponse> requestOrganizerEventUnpublish(
            String authorizationHeader,
            Long eventId,
            OrganizerEventUnpublishRequest request) {
        Map<String, Object> organizer = getAuthenticatedOrganizer(authorizationHeader);
        if (organizer.containsKey("message")) {
            return ApiResponse.fail(organizer.get("message").toString());
        }
        if (eventId == null || eventId <= 0) {
            return ApiResponse.fail("Invalid event id");
        }

        String reason = normalizeText(request == null ? null : request.reason());
        if (reason == null) {
            return ApiResponse.fail(400, "Unpublish reason is required");
        }
        if (reason.length() > 500) {
            return ApiResponse.fail(400, "Unpublish reason must not exceed 500 characters");
        }

        Long organizerUserId = ((Number) organizer.get("userId")).longValue();
        Map<String, Object> event = organizerRepository
                .findOrganizerEventDetail(organizerUserId, eventId).orElse(null);
        if (event == null) {
            return ApiResponse.fail(404, "Organizer event not found");
        }
        if (!WorkflowStatus.PUBLISHED.name().equals(statusText(event.get("workflowStatus")))) {
            return ApiResponse.fail(409, "Event cannot request unpublishing in its current workflow status");
        }
        if (organizerRepository.requestOrganizerEventUnpublish(organizerUserId, eventId) != 1) {
            return ApiResponse.fail(409, "Event workflow status changed before unpublish request");
        }

        LocalDateTime requestedAt = LocalDateTime.now();
        long unpublishRequestId = organizerRepository.createEventUnpublishRequest(
                organizerUserId, eventId, reason, requestedAt);
        notificationService.notifyAdminsEventUnpublishRequested(
                eventId,
                unpublishRequestId,
                normalizeText(event.get("eventTitle")),
                normalizeText(organizer.get("organizerName")));
        return ApiResponse.success("Organizer event unpublish requested successfully",
                new OrganizerEventUnpublishRequestResponse(
                        eventId,
                        unpublishRequestId,
                        WorkflowStatus.UNPUBLISH_REQUESTED.name(),
                        EventStatus.UNPUBLISH_REQUESTED.getStatus(),
                        EventStatus.UNPUBLISH_REQUESTED.getDescription(),
                        reason,
                        requestedAt,
                        List.of()));
    }

    private List<String> validateOrganizerEventPublish(
            Long eventId, Map<String, Object> event, int actualStallCount) {
        Set<String> errors = new LinkedHashSet<>();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startAt = toLocalDateTime(event.get("startAt"));
        LocalDateTime endAt = toLocalDateTime(event.get("endAt"));
        LocalDateTime registrationEndAt = toLocalDateTime(event.get("registrationEndAt"));

        if (normalizeText(event.get("coverImageUrl")) == null) errors.add("coverImage");
        if (normalizeText(event.get("mapImageUrl")) == null) errors.add("booth.mapImage");
        if (organizerRepository.findOrganizerEventCategories(eventId).isEmpty()) errors.add("categoryIds");
        if (organizerRepository.findOrganizerEventZones(eventId).isEmpty()) errors.add("booth.zones");

        Integer maxBooths = nullableInteger(event.get("maxBooths"));
        if (maxBooths == null || maxBooths <= 0 || actualStallCount != maxBooths) {
            errors.add("booth.stalls");
        }
        if (startAt == null || !startAt.isAfter(now)) errors.add("schedule.startAt");
        if (endAt == null || startAt == null || !endAt.isAfter(startAt)) errors.add("schedule.endAt");
        if (registrationEndAt == null || !registrationEndAt.isAfter(now)) {
            errors.add("schedule.registrationEndAt");
        }
        return List.copyOf(errors);
    }

    private List<String> validateOrganizerEventReview(
            Map<String, Object> event,
            List<Map<String, Object>> categories,
            List<Map<String, Object>> zones,
            List<Map<String, Object>> equipmentItems) {
        Set<String> errors = new LinkedHashSet<>();
        String title = normalizeText(event.get("eventTitle"));
        String summary = normalizeText(event.get("summary"));
        String description = normalizeText(event.get("description"));
        if (title == null || title.length() > 50) errors.add("eventTitle");
        if (summary == null || summary.length() > 300) errors.add("summary");
        if (description == null || description.length() > 800) errors.add("description");
        if (normalizeText(event.get("coverImageUrl")) == null) errors.add("coverImage");

        Set<Long> categoryIds = categories.stream()
                .map(category -> longValue(category.get("categoryId")))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (categoryIds.isEmpty()
                || organizerRepository.countActiveCategories(categoryIds) != categoryIds.size()) {
            errors.add("categoryIds");
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startAt = toLocalDateTime(event.get("startAt"));
        LocalDateTime endAt = toLocalDateTime(event.get("endAt"));
        LocalDateTime registrationStartAt = toLocalDateTime(event.get("registrationStartAt"));
        LocalDateTime registrationEndAt = toLocalDateTime(event.get("registrationEndAt"));
        if (startAt == null || !startAt.isAfter(now)) errors.add("schedule.startAt");
        if (endAt == null || startAt == null || !endAt.isAfter(startAt)) errors.add("schedule.endAt");
        if (registrationStartAt == null || !registrationStartAt.isAfter(now)) {
            errors.add("schedule.registrationStartAt");
        }
        if (registrationEndAt == null || registrationStartAt == null
                || !registrationEndAt.isAfter(registrationStartAt)
                || startAt == null || registrationEndAt.isAfter(startAt)) {
            errors.add("schedule.registrationEndAt");
        }

        requireText(errors, event, "locationName", "location.locationName");
        requireText(errors, event, "city", "location.city");
        requireText(errors, event, "district", "location.district");
        requireText(errors, event, "address", "location.address");
        requireText(errors, event, "trafficInfoMetro", "location.trafficInfoMetro");
        requireText(errors, event, "trafficInfoBus", "location.trafficInfoBus");
        requireText(errors, event, "trafficInfoDriving", "location.trafficInfoDriving");

        Integer maxBooths = nullableInteger(event.get("maxBooths"));
        BigDecimal stallWidth = toBigDecimal(event.get("stallWidth"));
        BigDecimal stallLength = toBigDecimal(event.get("stallLength"));
        BigDecimal baseFee = toBigDecimal(event.get("baseFee"));
        BigDecimal depositAmount = toBigDecimal(event.get("depositAmount"));
        if (maxBooths == null || maxBooths <= 0) errors.add("booth.maxBooths");
        if (!isPositive(stallWidth)) errors.add("booth.stallWidth");
        if (!isPositive(stallLength)) errors.add("booth.stallLength");
        if (!isNonNegative(baseFee)) errors.add("booth.baseFee");
        if (!isNonNegative(depositAmount)) errors.add("booth.depositAmount");
        if (normalizeText(event.get("mapImageUrl")) == null) errors.add("booth.mapImage");
        validateOrganizerEventReviewZones(errors, zones, maxBooths);

        Boolean providesEquipmentRental = nullableBoolean(event.get("providesEquipmentRental"));
        Boolean providesBasicPower = nullableBoolean(event.get("providesBasicPower"));
        Boolean allowsExtraPower = nullableBoolean(event.get("allowsExtraPower"));
        if (providesEquipmentRental == null) errors.add("equipment.providesEquipmentRental");
        if (providesBasicPower == null) errors.add("equipment.providesBasicPower");
        if (allowsExtraPower == null) errors.add("equipment.allowsExtraPower");
        if (Boolean.TRUE.equals(providesEquipmentRental)
                && equipmentItems.stream().noneMatch(item -> "EQUIPMENT".equals(statusText(item.get("itemType"))))) {
            errors.add("equipment.items");
        }
        if (Boolean.TRUE.equals(providesBasicPower)
                && equipmentItems.stream().noneMatch(item -> "POWER".equals(statusText(item.get("itemType")))
                        && "FREE".equals(statusText(item.get("chargeType"))))) {
            errors.add("equipment.basicPowerItems");
        }
        if (Boolean.TRUE.equals(allowsExtraPower)
                && equipmentItems.stream().noneMatch(item -> "POWER".equals(statusText(item.get("itemType")))
                        && "PAID".equals(statusText(item.get("chargeType"))))) {
            errors.add("equipment.extraPowerItems");
        }
        if (equipmentItems.stream().anyMatch(this::isInvalidOrganizerEventEquipmentRow)) {
            errors.add("equipment.items");
        }
        return List.copyOf(errors);
    }

    private void requireText(Set<String> errors, Map<String, Object> values, String key, String field) {
        if (normalizeText(values.get(key)) == null) errors.add(field);
    }

    private void validateOrganizerEventReviewZones(
            Set<String> errors, List<Map<String, Object>> zones, Integer maxBooths) {
        if (zones.isEmpty() || zones.size() > 26) {
            errors.add("booth.zones");
            return;
        }
        Set<String> names = new LinkedHashSet<>();
        int total = 0;
        for (Map<String, Object> zone : zones) {
            String name = normalizeText(zone.get("zoneName"));
            Integer stallCount = nullableInteger(zone.get("stallCount"));
            String color = normalizeText(zone.get("colorCode"));
            if (name == null || !name.matches("^[A-Z] 區$") || !names.add(name)) {
                errors.add("booth.zones");
            }
            if (stallCount == null || stallCount <= 0) {
                errors.add("booth.zones");
            } else {
                total += stallCount;
            }
            if (color == null || !color.matches("^#[0-9A-Fa-f]{6}$")) {
                errors.add("booth.zones");
            }
        }
        if (maxBooths != null && total != maxBooths) errors.add("booth.zones");
    }

    private boolean isInvalidOrganizerEventEquipmentRow(Map<String, Object> row) {
        String name = normalizeText(row.get("equipmentName"));
        String chargeType = statusText(row.get("chargeType"));
        String itemType = statusText(row.get("itemType"));
        String pricingUnit = statusText(row.get("pricingUnit"));
        String rentalStatus = statusText(row.get("rentalStatus"));
        BigDecimal rentalFee = toBigDecimal(row.get("rentalFee"));
        Integer stockQuantity = nullableInteger(row.get("stockQuantity"));
        Integer limit = nullableInteger(row.get("perStallRentalLimit"));
        Integer wattage = nullableInteger(row.get("wattageLimit"));
        if (name == null || name.length() > 100
                || !Set.of("FREE", "PAID").contains(chargeType)
                || !Set.of("EQUIPMENT", "POWER").contains(itemType)
                || !"DAY".equals(pricingUnit)
                || !"ACTIVE".equals(rentalStatus)
                || !isNonNegative(rentalFee)
                || (stockQuantity != null && stockQuantity < 0)
                || (limit != null && limit <= 0)) {
            return true;
        }
        return "EQUIPMENT".equals(itemType)
                ? normalizeText(row.get("unit")) == null || wattage != null
                : normalizeText(row.get("unit")) != null || wattage == null || wattage <= 0;
    }

    private String validateOrganizerEventDraft(OrganizerEventSaveRequest request) {
        String eventTitle = normalizeText(request.eventTitle());
        String summary = normalizeText(request.summary());
        if (eventTitle != null && eventTitle.length() > 200) {
            return "Event title must not exceed 200 characters";
        }
        if (summary != null && summary.length() > 300) {
            return "Event summary must not exceed 300 characters";
        }
        if (request.categoryIds().stream().anyMatch(id -> id == null || id <= 0)) {
            return "Event category ids must be positive";
        }

        OrganizerEventSaveRequest.Schedule schedule = request.schedule();
        if (schedule.startAt() != null && schedule.endAt() != null
                && schedule.endAt().isBefore(schedule.startAt())) {
            return "Event end time must not be before start time";
        }
        if (schedule.registrationStartAt() != null && schedule.registrationEndAt() != null
                && schedule.registrationEndAt().isBefore(schedule.registrationStartAt())) {
            return "Registration end time must not be before start time";
        }

        OrganizerEventSaveRequest.Location location = request.location();
        if (textLength(location.locationName()) > 200 || textLength(location.city()) > 50
                || textLength(location.district()) > 50 || textLength(location.address()) > 255) {
            return "Event location exceeds the database length limit";
        }

        OrganizerEventSaveRequest.Booth booth = request.booth();
        if ((booth.maxBooths() != null && booth.maxBooths() <= 0)
                || (booth.baseFee() != null && !isNonNegative(booth.baseFee()))
                || (booth.depositAmount() != null && !isNonNegative(booth.depositAmount()))
                || (booth.stallWidth() != null && !isPositive(booth.stallWidth()))
                || (booth.stallLength() != null && !isPositive(booth.stallLength()))) {
            return "Booth numbers and fees are invalid";
        }
        Set<String> zoneNames = new LinkedHashSet<>();
        if (booth.zones().size() > 26) {
            return "Booth zones must not exceed 26 items";
        }
        for (OrganizerEventSaveRequest.Zone zone : booth.zones()) {
            String zoneName = zone == null ? null : normalizeText(zone.zoneName());
            if (zoneName == null || !zoneName.matches("^[A-Z] 區$") || !zoneNames.add(zoneName)) {
                return "Booth zone names must use A to Z and be unique";
            }
            if (zone.stallCount() == null || zone.stallCount() <= 0) {
                return "Booth zone stall count must be greater than zero";
            }
            if (zone.colorCode() == null || !zone.colorCode().matches("^#[0-9A-Fa-f]{6}$")) {
                return "Booth zone color must use #RRGGBB format";
            }
        }

        for (OrganizerEventSaveRequest.Item item : request.equipment().items()) {
            String equipmentError = validateOrganizerEventEquipment(item);
            if (equipmentError != null) {
                return equipmentError;
            }
        }
        return null;
    }

    /**
     * 草稿可以缺少業務資料，但資料庫的 NOT NULL 欄位仍需有安全值。
     * 前端會提供相同預設；此處是直接呼叫 API 時的最後防線。
     */
    private OrganizerEventSaveRequest normalizeOrganizerEventDraft(OrganizerEventSaveRequest request) {
        OrganizerEventSaveRequest.Schedule sourceSchedule = request.schedule();
        OrganizerEventSaveRequest.Location sourceLocation = request.location();
        OrganizerEventSaveRequest.Booth sourceBooth = request.booth();
        List<OrganizerEventSaveRequest.Zone> zones = sourceBooth == null || sourceBooth.zones() == null
                ? List.of() : sourceBooth.zones();
        List<OrganizerEventSaveRequest.Item> items = request.equipment() == null
                || request.equipment().items() == null ? List.of() : request.equipment().items();

        return new OrganizerEventSaveRequest(
                request.eventId(), normalizeText(request.eventTitle()), normalizeText(request.summary()),
                normalizeText(request.description()),
                request.categoryIds() == null ? List.of() : request.categoryIds(),
                new OrganizerEventSaveRequest.Schedule(
                        sourceSchedule == null ? null : sourceSchedule.startAt(),
                        sourceSchedule == null ? null : sourceSchedule.endAt(),
                        sourceSchedule == null ? null : sourceSchedule.registrationStartAt(),
                        sourceSchedule == null ? null : sourceSchedule.registrationEndAt()),
                new OrganizerEventSaveRequest.Location(
                        sourceLocation == null ? null : normalizeText(sourceLocation.locationName()),
                        sourceLocation == null ? null : normalizeText(sourceLocation.city()),
                        sourceLocation == null ? null : normalizeText(sourceLocation.district()),
                        sourceLocation == null ? null : normalizeText(sourceLocation.address()),
                        sourceLocation == null ? null : normalizeText(sourceLocation.trafficInfoMetro()),
                        sourceLocation == null ? null : normalizeText(sourceLocation.trafficInfoBus()),
                        sourceLocation == null ? null : normalizeText(sourceLocation.trafficInfoDriving())),
                new OrganizerEventSaveRequest.Booth(
                        sourceBooth == null ? null : sourceBooth.maxBooths(),
                        sourceBooth == null ? null : sourceBooth.stallWidth(),
                        sourceBooth == null ? null : sourceBooth.stallLength(),
                        sourceBooth == null ? null : sourceBooth.baseFee(),
                        sourceBooth == null ? null : sourceBooth.depositAmount(),
                        zones),
                new OrganizerEventSaveRequest.Equipment(
                        request.equipment() == null ? null : request.equipment().providesEquipmentRental(),
                        request.equipment() == null ? null : request.equipment().providesBasicPower(),
                        request.equipment() == null ? null : request.equipment().allowsExtraPower(),
                        items));
    }

    private int textLength(String value) {
        return value == null ? 0 : value.trim().length();
    }

    private String validateOrganizerEventEquipment(OrganizerEventSaveRequest.Item item) {
        if (item == null || normalizeText(item.name()) == null || item.name().trim().length() > 100) {
            return "Equipment name is required and must not exceed 100 characters";
        }
        if (!Set.of("FREE", "PAID").contains(item.chargeType())
                || !Set.of("EQUIPMENT", "POWER").contains(item.itemType())
                || !"DAY".equals(item.pricingUnit())
                || !Set.of("ACTIVE", "UNACTIVE").contains(item.rentalStatus())
                || !isNonNegative(item.rentalFee())) {
            return "Equipment type, pricing, or status is invalid";
        }
        if (item.stockQuantity() != null && item.stockQuantity() < 0) {
            return "Equipment stock quantity must not be negative";
        }
        if (item.perStallRentalLimit() != null && item.perStallRentalLimit() <= 0) {
            return "Per-stall equipment limit must be greater than zero";
        }
        if ("EQUIPMENT".equals(item.itemType())) {
            if (normalizeText(item.unit()) == null || item.wattageLimit() != null) {
                return "Equipment unit is required and wattage must be empty";
            }
        } else if (normalizeText(item.unit()) != null
                || item.wattageLimit() == null || item.wattageLimit() <= 0) {
            return "Power item must have a valid wattage and no unit";
        }
        return null;
    }

    private boolean isPositive(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }

    private boolean isNonNegative(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) >= 0;
    }

    private OrganizerEventDetailResponse.Item toOrganizerEventEquipmentItem(Map<String, Object> row) {
        return new OrganizerEventDetailResponse.Item(
                longValue(row.get("eventEquipmentId")), normalizeText(row.get("equipmentGroupKey")),
                statusText(row.get("equipmentName")), toBigDecimal(row.get("rentalFee")),
                statusText(row.get("pricingUnit")), normalizeText(row.get("unit")),
                statusText(row.get("chargeType")), statusText(row.get("itemType")),
                normalizeText(row.get("equipmentDescription")), nullableInteger(row.get("stockQuantity")),
                nullableInteger(row.get("perStallRentalLimit")), statusText(row.get("rentalStatus")),
                nullableInteger(row.get("wattageLimit")));
    }

    private List<String> availableOrganizerEventActions(WorkflowStatus workflowStatus, EventStatus eventStatus) {
        return switch (workflowStatus) {
            case DRAFT -> List.of("EDIT", "SUBMIT_REVIEW", "DELETE");
            case PENDING_REVIEW -> List.of("WITHDRAW_REVIEW");
            case REVISION_REQUIRED -> List.of("EDIT", "RESUBMIT_REVIEW");
            case READY_TO_PUBLISH -> List.of("PUBLISH");
            case PUBLISHED -> List.of("REQUEST_UNPUBLISH");
            default -> List.of();
        };
    }

    private Integer nullableInteger(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    private OrganizerEventSummaryResponse toOrganizerEventSummary(Map<String, Object> event) {
        WorkflowStatus workflowStatus = WorkflowStatus.valueOf(statusText(event.get("workflowStatus")));
        int capacity = intValue(event.get("capacity"));
        int registeredCount = intValue(event.get("registeredCount"));
        EventStatus eventStatus = resolveOrganizerEventStatus(event, workflowStatus, capacity, registeredCount);
        return new OrganizerEventSummaryResponse(
                ((Number) event.get("eventId")).longValue(),
                statusText(event.get("eventTitle")),
                normalizeText(event.get("coverImageUrl")),
                toLocalDateTime(event.get("createdAt")),
                toLocalDateTime(event.get("eventStartAt")),
                toLocalDateTime(event.get("eventEndAt")),
                toLocalDateTime(event.get("registrationStartAt")),
                toLocalDateTime(event.get("registrationEndAt")),
                normalizeText(event.get("locationName")),
                normalizeText(event.get("city")),
                normalizeText(event.get("district")),
                normalizeText(event.get("address")),
                workflowStatus.name(),
                eventStatus.getStatus(),
                eventStatus.getDescription(),
                capacity,
                registeredCount,
                intValue(event.get("pendingReviewCount")),
                intValue(event.get("paidCount")),
                intValue(event.get("selectedCount")));
    }

    private EventStatus resolveOrganizerEventStatus(
            Map<String, Object> event,
            WorkflowStatus workflowStatus,
            int capacity,
            int registeredCount) {
        return switch (workflowStatus) {
            case DRAFT -> EventStatus.DRAFT;
            case PENDING_REVIEW -> EventStatus.PENDING_REVIEW;
            case REVISION_REQUIRED -> EventStatus.REVISION_REQUIRED;
            case MAP_BUILDING -> EventStatus.MAP_BUILDING;
            case READY_TO_PUBLISH -> EventStatus.READY_TO_PUBLISH;
            case UNPUBLISH_REQUESTED -> EventStatus.UNPUBLISH_REQUESTED;
            case UNPUBLISHED -> EventStatus.UNPUBLISHED;
            case CANCELLED -> EventStatus.UNPUBLISHED;
            case PUBLISHED -> {
                LocalDateTime now = LocalDateTime.now();
                LocalDateTime registrationStartAt = toLocalDateTime(event.get("registrationStartAt"));
                LocalDateTime registrationEndAt = toLocalDateTime(event.get("registrationEndAt"));
                LocalDateTime startAt = toLocalDateTime(firstPresent(event.get("eventStartAt"), event.get("startAt")));
                LocalDateTime endAt = toLocalDateTime(firstPresent(event.get("eventEndAt"), event.get("endAt")));
                if (registrationStartAt != null && now.isBefore(registrationStartAt)) {
                    yield EventStatus.PUBLISHED;
                }
                if (registrationEndAt != null && !now.isAfter(registrationEndAt)) {
                    yield registeredCount < capacity ? EventStatus.REGISTRATION_OPEN : EventStatus.FULL;
                }
                if (startAt != null && now.isBefore(startAt)) yield EventStatus.FINAL_CONFIRMATION;
                if (endAt != null && now.isAfter(endAt)) yield EventStatus.ENDED;
                yield EventStatus.ACTIVE;
            }
            case FINAL_REVIEW -> {
                LocalDateTime now = LocalDateTime.now();
                LocalDateTime startAt = toLocalDateTime(firstPresent(event.get("eventStartAt"), event.get("startAt")));
                LocalDateTime endAt = toLocalDateTime(firstPresent(event.get("eventEndAt"), event.get("endAt")));
                if (endAt != null && now.isAfter(endAt)) yield EventStatus.ENDED;
                if (startAt != null && !now.isBefore(startAt)) yield EventStatus.ACTIVE;
                yield EventStatus.PUBLISHED;
            }
        };
    }

    private EventStatus parseEventStatus(String status) {
        String normalized = normalizeText(status);
        if (normalized == null) return null;
        try {
            return EventStatus.valueOf(normalized.toUpperCase());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private Comparator<OrganizerEventSummaryResponse> organizerEventComparator(String sort) {
        LocalDateTime now = LocalDateTime.now();
        boolean upcomingFirst = "UPCOMING_FIRST".equals(sort);
        return (left, right) -> {
            int leftGroup = organizerEventSortGroup(left, now, upcomingFirst);
            int rightGroup = organizerEventSortGroup(right, now, upcomingFirst);
            int groupComparison = Integer.compare(leftGroup, rightGroup);
            if (groupComparison != 0) return groupComparison;

            Comparator<OrganizerEventSummaryResponse> withinGroup;
            boolean draftGroup = WorkflowStatus.DRAFT.name().equals(left.workflowStatus())
                    && WorkflowStatus.DRAFT.name().equals(right.workflowStatus());
            boolean endedGroup = left.eventEndAt() != null && left.eventEndAt().isBefore(now)
                    && right.eventEndAt() != null && right.eventEndAt().isBefore(now);
            if (draftGroup) {
                withinGroup = Comparator.comparing(OrganizerEventSummaryResponse::createdAt,
                        Comparator.nullsLast(Comparator.reverseOrder()));
            } else if (endedGroup) {
                withinGroup = Comparator.comparing(OrganizerEventSummaryResponse::eventEndAt,
                        Comparator.nullsLast(Comparator.reverseOrder()));
            } else {
                withinGroup = Comparator.comparing(OrganizerEventSummaryResponse::eventStartAt,
                        Comparator.nullsLast(Comparator.naturalOrder()));
            }
            return withinGroup.thenComparing(
                    OrganizerEventSummaryResponse::eventId,
                    Comparator.reverseOrder()).compare(left, right);
        };
    }

    private int organizerEventSortGroup(
            OrganizerEventSummaryResponse event,
            LocalDateTime now,
            boolean upcomingFirst) {
        boolean draft = WorkflowStatus.DRAFT.name().equals(event.workflowStatus());
        boolean unfinished = event.eventEndAt() == null || !event.eventEndAt().isBefore(now);
        if (!upcomingFirst && draft) return 0;
        if (unfinished && !draft) return upcomingFirst ? 0 : 1;
        if (!unfinished) return upcomingFirst ? 1 : 2;
        return 2;
    }

    private long longValue(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private ApiResponse<OrganizerAccountResponse> loadOrganizerAccount(String authorizationHeader, String successMessage) {
        String token = jwtService.extractTokenFromAuthorizationHeader(authorizationHeader);
        if (token == null || token.isBlank()) {
            return ApiResponse.fail("Authorization token is required");
        }
        if (!jwtService.isTokenValid(token)) {
            return ApiResponse.fail("Invalid or expired token");
        }

        String email = jwtService.getEmail(token);
        Map<String, Object> organizer = organizerRepository.findOrganizerAccountByEmail(email)
                .orElse(null);
        if (organizer == null) {
            return ApiResponse.fail("Organizer profile not found");
        }
        if (!"ORGANIZER".equals(organizer.get("role"))) {
            return ApiResponse.fail("This account is not an organizer");
        }

        Map<String, Object> account = new LinkedHashMap<>();
        account.put("organizerName", organizer.get("organizerName"));
        account.put("contactName", organizer.get("contactName"));
        account.put("contactPhone", organizer.get("contactPhone"));
        account.put("contactEmail", organizer.get("contactEmail"));
        account.put("companyName", organizer.get("companyName"));
        account.put("taxId", organizer.get("taxId"));
        account.put("city", organizer.get("city"));
        account.put("district", organizer.get("district"));
        account.put("address", organizer.get("address"));
        account.put("serviceDays", organizer.get("serviceDays"));
        account.put("serviceStartTime", formatServiceTime(organizer.get("serviceStartTime")));
        account.put("serviceEndTime", formatServiceTime(organizer.get("serviceEndTime")));
        return ApiResponse.success(successMessage, new OrganizerAccountResponse(account));
    }

    public ApiResponse<OrganizerAccountResponse> loadOrganizerProfile(String authorizationHeader) {
        return loadOrganizerAccount(authorizationHeader, "Organizer profile loaded successfully");
    }

    @Transactional
    public ApiResponse<OrganizerAccountResponse> saveOrganizerProfile(
            String authorizationHeader,
            OrganizerProfileSaveRequest body) {
        if (body == null) {
            return ApiResponse.fail("Organizer profile request is required");
        }

        Map<String, Object> organizer = getAuthenticatedOrganizer(authorizationHeader);
        if (organizer.containsKey("message")) {
            return ApiResponse.fail(organizer.get("message").toString());
        }

        String organizerName = normalizeText(body.getOrganizerName());
        String contactName = normalizeText(body.getContactName());
        String contactPhone = normalizeText(body.getContactPhone());
        String contactEmail = normalizeText(body.getContactEmail());
        String companyName = normalizeText(body.getCompanyName());
        String taxId = normalizeText(body.getTaxId());
        String city = normalizeText(body.getCity());
        String district = normalizeText(body.getDistrict());
        String address = normalizeText(body.getAddress());
        String serviceDays = normalizeText(body.getServiceDays());
        String serviceStartTimeText = normalizeText(body.getServiceStartTime());
        String serviceEndTimeText = normalizeText(body.getServiceEndTime());

        String validationError = validateOrganizerProfile(
                organizerName,
                contactName,
                contactPhone,
                contactEmail,
                companyName,
                taxId,
                city,
                district,
                address,
                serviceDays,
                serviceStartTimeText,
                serviceEndTimeText);
        if (validationError != null) {
            return ApiResponse.fail(validationError);
        }

        LocalTime serviceStartTime = parseServiceTime(serviceStartTimeText);
        LocalTime serviceEndTime = parseServiceTime(serviceEndTimeText);

        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("organizerName", organizerName);
        profile.put("contactName", contactName);
        profile.put("contactPhone", contactPhone);
        profile.put("contactEmail", contactEmail);
        profile.put("companyName", companyName);
        profile.put("taxId", taxId);
        profile.put("city", city);
        profile.put("district", district);
        profile.put("address", address);
        profile.put("serviceDays", serviceDays);
        profile.put("serviceStartTime", serviceStartTime);
        profile.put("serviceEndTime", serviceEndTime);

        Long organizerUserId = ((Number) organizer.get("userId")).longValue();
        boolean profileAlreadyExists = normalizeText(organizer.get("organizerName")) != null;
        organizerRepository.saveOrganizerProfile(organizerUserId, profile);
        if (profileAlreadyExists) {
            notificationService.notifyOrganizerProfileResubmitted(organizerUserId, organizerName);
        }
        ApiResponse<OrganizerAccountResponse> loaded = loadOrganizerAccount(authorizationHeader, "Organizer profile loaded successfully");
        if (!loaded.isSuccessStatus()) {
            return loaded;
        }
        return ApiResponse.success("Organizer profile saved successfully", loaded.getData());
    }

    public ApiResponse<OrganizerAccountingSearchResponse> searchOrganizerAccounts(
            String authorizationHeader,
            String eventTitle,
            String status,
            LocalDate eventStartAt,
            LocalDate eventEndAt,
            Integer page,
            Integer pageSize) {
        Map<String, Object> organizer = getAuthenticatedOrganizer(authorizationHeader);
        if (organizer.containsKey("message")) {
            return ApiResponse.fail(organizer.get("message").toString());
        }

        Long organizerUserId = ((Number) organizer.get("userId")).longValue();
        LocalDateTime startAt = eventStartAt == null ? null : eventStartAt.atStartOfDay();
        LocalDateTime endExclusive = eventEndAt == null ? null : eventEndAt.plusDays(1).atStartOfDay();
        List<OrganizerAccountingSummaryResponse> accounts = organizerRepository
                .findOrganizerAccountingEvents(organizerUserId, eventTitle, startAt, endExclusive)
                .stream()
                .map(this::withDisplayPublishStatus)
                .filter(account -> matchesPublishStatus(account, status))
                .map(this::toAccountingSummaryResponse)
                .map(OrganizerAccountingSummaryResponse::new)
                .toList();

        return ApiResponse.success(
                "Organizer accounting list retrieved successfully",
                new OrganizerAccountingSearchResponse(PageResponse.from(accounts, page, pageSize)));
    }

    public ApiResponse<OrganizerPaymentSearchResponse> searchOrganizerPayments(
            String authorizationHeader,
            String keyword,
            String paymentStatus,
            LocalDate startDate,
            LocalDate endDate,
            Integer page,
            Integer pageSize) {
        Map<String, Object> organizer = getAuthenticatedOrganizer(authorizationHeader);
        if (organizer.containsKey("message")) {
            return ApiResponse.fail(organizer.get("message").toString());
        }
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            return ApiResponse.fail(400, "開始日不可晚於結束日");
        }

        String normalizedStatus = normalizePaymentStatusFilter(paymentStatus);
        if (paymentStatus != null && !paymentStatus.isBlank() && normalizedStatus == null) {
            return ApiResponse.fail(400, "付款狀態僅接受：待付款、付款成功、退款申請中、退款處理中、付款失敗、付款逾期、退款失敗、已退款");
        }

        Long organizerUserId = ((Number) organizer.get("userId")).longValue();
        LocalDateTime paidStartAt = startDate == null ? null : startDate.atStartOfDay();
        LocalDateTime paidEndExclusive = endDate == null ? null : endDate.plusDays(1).atStartOfDay();
        List<OrganizerPaymentSummaryResponse> payments = organizerRepository
                .findOrganizerPayments(
                        organizerUserId,
                        normalizeText(keyword),
                        normalizedStatus,
                        paidStartAt,
                        paidEndExclusive)
                .stream()
                .map(this::toOrganizerPaymentSummaryResponse)
                .toList();

        return ApiResponse.success(
                "Organizer payments retrieved successfully",
                new OrganizerPaymentSearchResponse(PageResponse.from(payments, page, pageSize)));
    }

    private OrganizerPaymentSummaryResponse toOrganizerPaymentSummaryResponse(Map<String, Object> payment) {
        LocalDateTime paymentTime = toLocalDateTime(payment.get("paymentTime"));
        return new OrganizerPaymentSummaryResponse(orderedMap(
                "applicationId", payment.get("applicationId"),
                "eventCoverImageUrl", payment.get("eventCoverImageUrl"),
                "eventTitle", payment.get("eventTitle"),
                "brandName", payment.get("brandName"),
                "vendorName", payment.get("vendorName"),
                "applicationStatus", applicationStatusService.resolveApplicationStatus(payment),
                "paymentAmount", payment.get("paymentAmount"),
                "depositAmount", payment.get("depositAmount"),
                "paymentTime", paymentTime == null ? null : paymentTime.format(SPACE_DATE_TIME_FORMATTER),
                "paymentStatus", paymentStatusDescription(payment.get("paymentStage"))));
    }

    private String normalizePaymentStatusFilter(String paymentStatus) {
        if (paymentStatus == null || paymentStatus.isBlank()) {
            return null;
        }
        return switch (paymentStatus.trim().toUpperCase()) {
            case "PENDING", "待付款" -> "PENDING";
            case "PAID", "付款成功", "已付款" -> "PAID";
            case "FAILED", "付款失敗" -> "FAILED";
            case "EXPIRED", "付款逾期", "已逾期" -> "EXPIRED";
            case "REFUND_REQUESTED", "退款申請中" -> "REFUND_REQUESTED";
            case "REFUNDING", "退款處理中" -> "REFUNDING";
            case "REFUND_FAILED", "退款失敗" -> "REFUND_FAILED";
            case "REFUNDED", "已退款" -> "REFUNDED";
            default -> null;
        };
    }

    private String paymentStatusDescription(Object status) {
        return switch (statusText(status) == null ? "" : statusText(status)) {
            case "PENDING" -> "待付款";
            case "PAID" -> "付款成功";
            case "FAILED" -> "付款失敗";
            case "EXPIRED" -> "付款逾期";
            case "REFUND_REQUESTED" -> "退款申請中";
            case "REFUNDING" -> "退款處理中";
            case "REFUND_FAILED" -> "退款失敗";
            case "REFUNDED" -> "已退款";
            default -> statusText(status);
        };
    }

    public ApiResponse<MapBackedResponse> getOrganizerAccountDetail(
            String authorizationHeader,
            Long eventId,
            String status,
            Integer paymentPage,
            Integer paymentPageSize) {
        Map<String, Object> response = buildOrganizerAccountDetail(authorizationHeader, eventId, status);
        if (response.containsKey("message")) {
            return ApiResponse.fail(response.get("message").toString());
        }
        paginateListField(response, "payments", paymentPage, paymentPageSize);

        return ApiResponse.success(
                "Organizer accounting detail retrieved successfully",
                new MapBackedResponse(response));
    }

    public ReportExport exportOrganizerAccountReport(
            String authorizationHeader,
            Long eventId,
            String status) {
        Map<String, Object> response = buildOrganizerAccountDetail(authorizationHeader, eventId, status);
        if (response.containsKey("message")) {
            return ReportExport.fail(response.get("message").toString());
        }
        return ReportExport.excel(
                reportFilename("account-report", response.get("event"), eventId),
                buildAccountReportWorkbook(response));
    }

    private Map<String, Object> buildOrganizerAccountDetail(
            String authorizationHeader,
            Long eventId,
            String status) {
        Map<String, Object> organizer = getAuthenticatedOrganizer(authorizationHeader);
        if (organizer.containsKey("message")) {
            return organizer;
        }
        if (eventId == null) {
            return message("Event id is required");
        }

        Long organizerUserId = ((Number) organizer.get("userId")).longValue();
        Map<String, Object> account = organizerRepository
                .findOrganizerAccountingEventDetail(organizerUserId, eventId)
                .orElse(null);
        if (account == null) {
            return message("Event not found");
        }

        List<Map<String, Object>> payments = organizerRepository
                .findOrganizerAccountingPaymentDetails(eventId)
                .stream()
                .map(this::toAccountingPaymentDetailResponse)
                .filter(payment -> matchesAccountingStatus(payment, status))
                .toList();

        return orderedMap(
                "event", toAccountingEventResponse(withDisplayPublishStatus(account)),
                "summary", toAccountingFinancialSummary(account),
                "statistics", toAccountingStatistics(account),
                "payments", payments);
    }

    @SuppressWarnings("unchecked")
    private void paginateListField(Map<String, Object> response, String fieldName, Integer page, Integer pageSize) {
        Object value = response.get(fieldName);
        if (!(value instanceof List<?>)) {
            return;
        }
        response.put(fieldName, toPagedMap((List<Map<String, Object>>) value, page, pageSize));
    }

    @SuppressWarnings("unchecked")
    private void paginateItemsField(Map<String, Object> response, String fieldName, Integer page, Integer pageSize) {
        Object value = response.get(fieldName);
        if (!(value instanceof Map<?, ?> section)) {
            return;
        }
        Object items = section.get("items");
        if (!(items instanceof List<?>)) {
            return;
        }
        response.put(fieldName, toPagedMap((List<Map<String, Object>>) items, page, pageSize));
    }

    private Map<String, Object> toPagedMap(List<Map<String, Object>> items, Integer page, Integer pageSize) {
        PageResponse<Map<String, Object>> paged = PageResponse.from(items, page, pageSize);
        return orderedMap(
                "totalCount", paged.getTotalItems(),
                "items", paged.getItems(),
                "page", paged.getPage(),
                "pageSize", paged.getPageSize(),
                "totalItems", paged.getTotalItems(),
                "totalPages", paged.getTotalPages(),
                "hasPrevious", paged.isHasPrevious(),
                "hasNext", paged.isHasNext());
    }

    public ApiResponse<OrganizerApplicationSearchResponse> searchOrganizerApplications(
            String authorizationHeader,
            String eventTitle,
            String status,
            String brandName,
            LocalDate registrationStartAt,
            LocalDate registrationEndAt,
            Integer page,
            Integer pageSize) {
        Map<String, Object> organizer = getAuthenticatedOrganizer(authorizationHeader);
        if (organizer.containsKey("message")) {
            return ApiResponse.fail(organizer.get("message").toString());
        }

        Long organizerUserId = ((Number) organizer.get("userId")).longValue();
        Map<String, Object> summary = organizerRepository.findOrganizerApplicationTaskSummary(organizerUserId);
        OrganizerTaskSummaryResponse taskSummary = new OrganizerTaskSummaryResponse(
                longValue(summary.get("pendingReviewCount")),
                longValue(summary.get("pendingRefundConfirmationCount")),
                longValue(summary.get("pendingStallSelectionCount")));
        LocalDateTime appliedStartAt = registrationStartAt == null ? null : registrationStartAt.atStartOfDay();
        LocalDateTime appliedEndExclusive = registrationEndAt == null ? null : registrationEndAt.plusDays(1).atStartOfDay();
        List<Map<String, Object>> applicationRows = organizerRepository
                .findOrganizerApplications(organizerUserId, eventTitle, brandName, appliedStartAt, appliedEndExclusive);
        Map<Long, CategoryResponse> categoryByVendorProfileId = categoryByVendorProfileId(applicationRows);
        List<OrganizerApplicationSummaryResponse> applications = applicationRows
                .stream()
                .map(this::withDisplayApplicationStatus)
                .filter(application -> matchesApplicationStatus(application, status))
                .map(application -> toApplicationSummaryResponse(application, categoryByVendorProfileId))
                .map(OrganizerApplicationSummaryResponse::new)
                .toList();
        return ApiResponse.success(
                "Organizer applications retrieved successfully",
                new OrganizerApplicationSearchResponse(
                        taskSummary,
                        PageResponse.from(applications, page, pageSize)));
    }

    public ApiResponse<OrganizerStallEventSearchResponse> searchOrganizerStallEvents(
            String authorizationHeader,
            String eventTitle,
            String status,
            LocalDate eventStartAt,
            LocalDate eventEndAt,
            Integer page,
            Integer pageSize) {
        Map<String, Object> organizer = getAuthenticatedOrganizer(authorizationHeader);
        if (organizer.containsKey("message")) {
            return ApiResponse.fail(organizer.get("message").toString());
        }

        Long organizerUserId = ((Number) organizer.get("userId")).longValue();
        LocalDateTime startAt = eventStartAt == null ? null : eventStartAt.atStartOfDay();
        LocalDateTime endExclusive = eventEndAt == null ? null : eventEndAt.plusDays(1).atStartOfDay();
        List<OrganizerStallEventSummaryResponse> events = organizerRepository
                .findOrganizerStallEvents(organizerUserId, eventTitle, startAt, endExclusive)
                .stream()
                .map(this::withDisplayStallEventStatus)
                .filter(event -> matchesStallEventStatus(event, status))
                .map(this::toStallEventSummaryResponse)
                .map(OrganizerStallEventSummaryResponse::new)
                .toList();

        return ApiResponse.success(
                "Organizer stall events retrieved successfully",
                new OrganizerStallEventSearchResponse(PageResponse.from(events, page, pageSize)));
    }

    public ApiResponse<OrganizerEquipmentSearchResponse> searchOrganizerEquipmentEvents(
            String authorizationHeader,
            String eventTitle,
            String status,
            LocalDate eventStartAt,
            LocalDate eventEndAt,
            Integer page,
            Integer pageSize) {
        Map<String, Object> organizer = getAuthenticatedOrganizer(authorizationHeader);
        if (organizer.containsKey("message")) {
            return ApiResponse.fail(organizer.get("message").toString());
        }

        Long organizerUserId = ((Number) organizer.get("userId")).longValue();
        LocalDateTime startAt = eventStartAt == null ? null : eventStartAt.atStartOfDay();
        LocalDateTime endExclusive = eventEndAt == null ? null : eventEndAt.plusDays(1).atStartOfDay();
        List<OrganizerEquipmentSummaryResponse> events = organizerRepository
                .findOrganizerEquipmentEvents(organizerUserId, eventTitle, startAt, endExclusive)
                .stream()
                .map(this::withDisplayEquipmentEventStatus)
                .filter(event -> matchesEquipmentEventStatus(event, status))
                .map(this::toEquipmentSummaryResponse)
                .map(OrganizerEquipmentSummaryResponse::new)
                .toList();

        return ApiResponse.success(
                "Organizer equipment events retrieved successfully",
                new OrganizerEquipmentSearchResponse(PageResponse.from(events, page, pageSize)));
    }

    public ApiResponse<MapBackedResponse> getOrganizerEquipmentDetail(
            String authorizationHeader,
            Long eventId,
            Integer equipmentRentalPage,
            Integer equipmentRentalPageSize,
            Integer extraPowerPage,
            Integer extraPowerPageSize,
            Integer vehiclePage,
            Integer vehiclePageSize) {
        Map<String, Object> response = buildOrganizerEquipmentDetail(authorizationHeader, eventId);
        if (response.containsKey("message")) {
            return ApiResponse.fail(response.get("message").toString());
        }
        paginateItemsField(response, "equipmentRentalManagement", equipmentRentalPage, equipmentRentalPageSize);
        paginateItemsField(response, "extraPowerManagement", extraPowerPage, extraPowerPageSize);
        paginateItemsField(response, "vehicleManagement", vehiclePage, vehiclePageSize);

        return ApiResponse.success(
                "Organizer equipment detail retrieved successfully",
                new MapBackedResponse(response));
    }

    public ReportExport exportOrganizerEquipmentReport(String authorizationHeader, Long eventId) {
        Map<String, Object> response = buildOrganizerEquipmentDetail(authorizationHeader, eventId);
        if (response.containsKey("message")) {
            return ReportExport.fail(response.get("message").toString());
        }
        return ReportExport.excel(
                reportFilename("equipment-report", response.get("event"), eventId),
                buildEquipmentReportWorkbook(response));
    }

    private Map<String, Object> buildOrganizerEquipmentDetail(String authorizationHeader, Long eventId) {
        Map<String, Object> organizer = getAuthenticatedOrganizer(authorizationHeader);
        if (organizer.containsKey("message")) {
            return organizer;
        }
        if (eventId == null) {
            return message("Event id is required");
        }

        Long organizerUserId = ((Number) organizer.get("userId")).longValue();
        Map<String, Object> event = organizerRepository
                .findOrganizerEquipmentEventDetail(organizerUserId, eventId)
                .orElse(null);
        if (event == null) {
            return message("Event not found");
        }

        List<Map<String, Object>> equipments = organizerRepository.findEventEquipments(eventId);
        List<Map<String, Object>> rentalStats = organizerRepository.findOrganizerEquipmentRentalStats(eventId);
        List<Map<String, Object>> equipmentManagementRows = organizerRepository.findOrganizerEquipmentManagementRows(eventId);
        List<Map<String, Object>> powerManagementRows = organizerRepository.findOrganizerPowerManagementRows(eventId);
        List<Map<String, Object>> vehicleManagementRows = organizerRepository.findOrganizerVehicleManagementRows(eventId);

        return orderedMap(
                "event", toEquipmentDetailEventResponse(withDisplayEquipmentEventStatus(event)),
                "equipmentOverview", toEquipmentOverview(rentalStats, vehicleManagementRows),
                "eventEquipments", toEventEquipmentRows(equipments),
                "basicPowers", toBasicPowerRows(equipments),
                "extraPowers", toExtraPowerRows(equipments),
                "equipmentRentalStatistics", toEquipmentRentalStatistics(rentalStats),
                "extraPowerApplicationStatistics", toExtraPowerApplicationStatistics(rentalStats),
                "vehicleRegistrationStatistics", toVehicleRegistrationStatistics(vehicleManagementRows),
                "equipmentRentalManagement", toEquipmentRentalManagement(vehicleManagementRows, equipmentManagementRows, equipments),
                "extraPowerManagement", toExtraPowerManagement(vehicleManagementRows, powerManagementRows),
                "vehicleManagement", toVehicleManagement(vehicleManagementRows));
    }

    public ApiResponse<OrganizerApplicationDetailResponse> getOrganizerApplicationDetail(String authorizationHeader, Long applicationId) {
        Map<String, Object> organizer = getAuthenticatedOrganizer(authorizationHeader);
        if (organizer.containsKey("message")) {
            return ApiResponse.fail(organizer.get("message").toString());
        }
        if (applicationId == null) {
            return ApiResponse.fail("Application id is required");
        }

        Long organizerUserId = ((Number) organizer.get("userId")).longValue();
        Map<String, Object> application = organizerRepository
                .findOrganizerApplicationDetail(organizerUserId, applicationId)
                .orElse(null);
        if (application == null) {
            return ApiResponse.fail("Application not found");
        }

        Map<String, Object> response = buildApplicationDetailResponse(applicationId, application);
        return ApiResponse.success(
                "Organizer application detail retrieved successfully",
                new OrganizerApplicationDetailResponse(response));
    }

    public ApiResponse<OrganizerPaymentDetailResponse> getOrganizerPaymentDetail(
            String authorizationHeader,
            Long applicationId) {
        Map<String, Object> organizer = getAuthenticatedOrganizer(authorizationHeader);
        if (organizer.containsKey("message")) {
            return ApiResponse.fail(organizer.get("message").toString());
        }
        if (applicationId == null) {
            return ApiResponse.fail("報名 ID 不可為空");
        }

        Long organizerUserId = ((Number) organizer.get("userId")).longValue();
        Map<String, Object> application = organizerRepository
                .findOrganizerApplicationDetail(organizerUserId, applicationId)
                .orElse(null);
        if (application == null) {
            return ApiResponse.fail("找不到此付款報名，或該報名不屬於目前登入的主辦方");
        }
        if (!"APPROVED".equals(statusText(application.get("reviewStatus")))
                || application.get("paymentNo") == null) {
            return ApiResponse.fail("此報名尚未進入付款階段，無付款詳情可顯示");
        }

        return ApiResponse.success(
                "付款詳情取得成功",
                new OrganizerPaymentDetailResponse(buildPaymentDetailResponse(applicationId, application)));
    }

    public Map<String, Object> buildPaymentDetailResponse(
            Long applicationId,
            Map<String, Object> rawApplication) {
        Map<String, Object> application = withDisplayApplicationStatus(rawApplication);
        List<Map<String, Object>> applicationDates = organizerRepository.findApplicationDates(applicationId);
        List<Map<String, Object>> rentalRows = toEquipmentRentalResponses(
                organizerRepository.findApplicationEquipmentRentals(applicationId));
        List<Map<String, Object>> eventEquipments = organizerRepository.findEventEquipments(
                toLong(application.get("eventId")));
        Integer rentalDays = applicationDates.isEmpty()
                ? applicationDays(application.get("applyDates"))
                : applicationDates.size();
        BigDecimal applicationFee = multiply(application.get("baseFee"), rentalDays);
        BigDecimal equipmentFee = sumEquipmentRentalFee(rentalRows, "EQUIPMENT");
        BigDecimal powerFee = sumEquipmentRentalFee(rentalRows, "POWER");
        boolean hasRefund = normalizeText(application.get("refundStatus")) != null;

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("event", orderedMap(
                "eventId", application.get("eventId"),
                "eventCoverImageUrl", application.get("eventCoverImageUrl"),
                "eventTitle", application.get("eventTitle"),
                "eventStatus", displayEventStatus(application),
                "eventDate", formatEventDateWithWeekday(application),
                "eventTime", formatEventTime(application),
                "locationName", application.get("locationName"),
                "address", joinAddress(application.get("eventCity"), application.get("eventDistrict"), application.get("eventAddress"))));
        response.put("application", orderedMap(
                "applicationId", application.get("applicationId"),
                "applicationStatus", application.get("applicationStatus"),
                "paymentStatus", displayPaymentStatus(application),
                "applicationNo", application.get("applicationNo"),
                "paymentNo", application.get("paymentNo")));
        response.put("statusRecords", toApplicationStatusFlow(application));
        response.put("vendor", orderedMap(
                "vendorName", application.get("vendorOwnerName"),
                "phone", application.get("vendorPhone"),
                "email", firstPresent(application.get("vendorContactEmail"), application.get("vendorEmail")),
                "address", joinAddress(application.get("vendorCity"), application.get("vendorDistrict"), application.get("vendorAddress"))));
        response.put("brand", orderedMap(
                "brandName", application.get("vendorName"),
                "avatarImageUrl", application.get("vendorAvatarUrl"),
                "category", vendorCategory(toLong(application.get("vendorProfileId"))),
                "introduction", firstPresent(application.get("brandSummary"), application.get("brandDescription"))));
        response.put("payment", orderedMap(
                "paymentMethod", paymentMethod(application.get("paymentProvider")),
                "paymentPlatform", application.get("paymentProvider"),
                "paymentTradeNo", application.get("paymentProviderTradeNo"),
                "paidAt", formatDateTime(application.get("paidAt"))));
        response.put("feeDetails", toFeeDetail(
                application.get("baseFee"), rentalDays, applicationDates, rentalRows,
                applicationFee, equipmentFee, powerFee,
                application.get("depositAmount"), application.get("totalAmount")));
        response.put("refund", hasRefund ? orderedMap(
                "refundStatus", displayRefundStatus(application.get("refundStatus")),
                "refundMethod", "原付款方式退回",
                "paymentPlatform", application.get("paymentProvider"),
                "refundTradeNo", application.get("refundNo"),
                "refundedAt", formatDateTime(application.get("refundedAt"))) : null);
        response.put("refundDetails", hasRefund
                ? toRefundFeeDetail(applicationFee, equipmentFee, powerFee,
                        application.get("depositAmount"), application.get("refundAmount"))
                : null);
        response.put("basicEquipments", basicEquipmentResponses(eventEquipments));
        response.put("basicPower", basicPowerResponses(eventEquipments));
        response.put("rentalEquipments", rentalRows.stream()
                .filter(row -> "PAID".equals(statusText(row.get("chargeType"))))
                .filter(row -> "EQUIPMENT".equals(statusText(row.get("itemType"))))
                .map(row -> paidEquipmentDetail(row, rentalDays)).toList());
        response.put("extraPower", rentalRows.stream()
                .filter(row -> "PAID".equals(statusText(row.get("chargeType"))))
                .filter(row -> "POWER".equals(statusText(row.get("itemType"))))
                .map(row -> paidPowerDetail(row, rentalDays)).toList());
        return response;
    }

    public Map<String, Object> buildApplicationDetailResponse(
            Long applicationId,
            Map<String, Object> application) {
        List<Map<String, Object>> equipmentRentals = organizerRepository.findApplicationEquipmentRentals(applicationId);
        List<Map<String, Object>> applicationDates = organizerRepository.findApplicationDates(applicationId);
        Map<String, Object> response = toApplicationDetailResponse(
                withDisplayApplicationStatus(application),
                applicationDates,
                equipmentRentals);
        response.put("status", toApplicationStatusFlow(application));
        return response;
    }

    @Transactional
    public ApiResponse<MapBackedResponse> approveOrganizerApplication(
            String authorizationHeader,
            Long applicationId) {
        return reviewOrganizerApplication(
                authorizationHeader,
                applicationId,
                "APPROVED",
                null,
                null);
    }

    @Transactional
    public ApiResponse<MapBackedResponse> rejectOrganizerApplication(
            String authorizationHeader,
            Long applicationId,
            OrganizerApplicationReviewRequest body) {
        return reviewOrganizerApplication(
                authorizationHeader,
                applicationId,
                "REJECTED",
                body == null ? null : body.getReviewNote(),
                body == null ? null : body.getReviewNoteDetail());
    }

    private ApiResponse<MapBackedResponse> reviewOrganizerApplication(
            String authorizationHeader,
            Long applicationId,
            String reviewStatus,
            String rawReviewNote,
            String rawReviewNoteDetail) {
        Map<String, Object> organizer = getAuthenticatedOrganizer(authorizationHeader);
        if (organizer.containsKey("message")) {
            return ApiResponse.fail(organizer.get("message").toString());
        }
        if (applicationId == null) {
            return ApiResponse.fail("Application id is required");
        }
        if (reviewStatus == null) {
            return ApiResponse.fail("Review status is invalid");
        }
        String reviewNote = null;
        String reviewNoteDetail = null;
        if ("REJECTED".equals(reviewStatus)) {
            reviewNote = normalizeText(rawReviewNote);
            reviewNoteDetail = normalizeText(rawReviewNoteDetail);
            if (reviewNote == null) {
                return ApiResponse.fail("Review note is required");
            }
        }

        Long organizerUserId = ((Number) organizer.get("userId")).longValue();
        Map<String, Object> application = organizerRepository
                .findOrganizerApplicationDetail(organizerUserId, applicationId)
                .orElse(null);
        if (application == null) {
            return ApiResponse.fail("Application not found");
        }
        if (!"PENDING".equals(statusText(application.get("reviewStatus")))) {
            return ApiResponse.fail("Application has already been reviewed");
        }
        if (isTrue(application.get("isCancelled"))) {
            return ApiResponse.fail("Application has been cancelled");
        }

        int updatedRows = organizerRepository.updateApplicationReviewStatus(
                organizerUserId,
                applicationId,
                reviewStatus);
        if (updatedRows == 0) {
            return ApiResponse.fail("Application review failed");
        }
        if ("REJECTED".equals(reviewStatus)) {
            organizerRepository.insertApplicationReviewNote(
                    applicationId,
                    reviewNote,
                    reviewNoteDetail);
        }

        notificationService.notifyApplicationReviewed(
                ((Number) application.get("vendorUserId")).longValue(),
                applicationId,
                statusText(application.get("eventTitle")),
                "APPROVED".equals(reviewStatus));

        return ApiResponse.success(
                "Organizer application reviewed successfully",
                new MapBackedResponse(orderedMap(
                        "applicationId", applicationId,
                        "applicationNo", application.get("applicationNo"),
                        "reviewStatus", reviewStatus,
                        "reviewNote", reviewNote,
                        "reviewNoteDetail", reviewNoteDetail)));
    }

    @Transactional
    public ApiResponse<MapBackedResponse> refundOrganizerDeposit(
            String authorizationHeader,
            Long applicationId) {
        Map<String, Object> organizer = getAuthenticatedOrganizer(authorizationHeader);
        if (organizer.containsKey("message")) {
            return ApiResponse.fail(401, organizer.get("message").toString());
        }
        if (applicationId == null) {
            return ApiResponse.fail(400, "請提供 applicationId");
        }

        Long organizerUserId = ((Number) organizer.get("userId")).longValue();
        Map<String, Object> application = organizerRepository.findDepositRefundCandidate(
                organizerUserId,
                applicationId).orElse(null);
        if (application == null) {
            return ApiResponse.fail(404, "找不到報名單，或該報名活動不屬於目前主辦方");
        }
        if (!isTrue(application.get("eventOngoing"))) {
            return ApiResponse.fail(409, "目前不在活動進行時間內，無法退還保證金");
        }
        if (isTrue(application.get("isCancelled"))) {
            return ApiResponse.fail(409, "此報名單已取消，無法退還保證金");
        }
        String refundStatus = statusText(application.get("refundStatus"));
        if (refundStatus != null
                && Set.of("REFUND_REQUESTED", "REFUNDING", "REFUND_FAILED", "REFUNDED").contains(refundStatus)) {
            return ApiResponse.fail(409, "此報名單已有退款流程（" + refundStatus + "），無法退還保證金");
        }
        if (!"APPROVED".equals(statusText(application.get("reviewStatus")))
                || !"PAID".equals(statusText(application.get("paymentStatus")))) {
            return ApiResponse.fail(409, "此報名單尚未完成審核及付款，無法退還保證金");
        }
        BigDecimal depositAmount = toBigDecimal(application.get("depositAmount"));
        if (depositAmount == null || depositAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return ApiResponse.fail(409, "此報名單沒有可退還的保證金");
        }
        if ("RETURNED".equals(statusText(application.get("depositStatus")))) {
            return ApiResponse.fail(409, "此報名單的保證金已經退還，請勿重複操作");
        }

        Long applicationDateCountValue = toLong(application.get("applicationDateCount"));
        Long selectedStallCountValue = toLong(application.get("selectedStallCount"));
        long applicationDateCount = applicationDateCountValue == null ? 0L : applicationDateCountValue;
        long selectedStallCount = selectedStallCountValue == null ? 0L : selectedStallCountValue;
        if (applicationDateCount == 0 || selectedStallCount != applicationDateCount) {
            return ApiResponse.fail(409, "此報名單尚未完成所有活動日期的選位，無法退還保證金");
        }
        if (organizerRepository.markDepositReturned(
                organizerUserId,
                applicationId) != 1) {
            return ApiResponse.fail(409, "保證金狀態已變更，請重新整理後再試");
        }
        notificationService.notifyDepositReturned(
                toLong(application.get("vendorUserId")),
                applicationId,
                statusText(application.get("eventTitle")));

        return ApiResponse.success(
                "保證金現金退還登記成功",
                new MapBackedResponse(orderedMap(
                        "applicationId", applicationId,
                        "applicationNo", application.get("applicationNo"),
                        "eventId", application.get("eventId"),
                        "userId", application.get("vendorUserId"),
                        "depositAmount", application.get("depositAmount"),
                        "depositStatus", "RETURNED",
                        "refundMethod", "CASH")));
    }

    private Map<String, Object> withDisplayApplicationStatus(Map<String, Object> application) {
        Map<String, Object> response = new LinkedHashMap<>(application);
        response.put("applicationStatus", applicationStatusService.resolveApplicationStatus(application));
        return response;
    }

    private Map<String, Object> withDisplayPublishStatus(Map<String, Object> account) {
        Map<String, Object> response = new LinkedHashMap<>(account);
        response.put("publishStatusText", displayPublishStatus(account.get("publishStatus")));
        response.put("statusNote", displayRegistrationProgress(account));
        return response;
    }

    private boolean matchesPublishStatus(Map<String, Object> account, String status) {
        String normalizedStatus = normalizeText(status);
        if (normalizedStatus == null
                || "\u5168\u90e8\u72c0\u614b".equals(normalizedStatus)
                || "?券".equals(normalizedStatus)) {
            return true;
        }
        return normalizedStatus.toUpperCase().equals(statusText(account.get("publishStatus")))
                || normalizedStatus.equals(normalizeText(account.get("publishStatusText")));
    }

    private boolean matchesApplicationStatus(Map<String, Object> application, String status) {
        String normalizedStatus = normalizeText(status);
        if (normalizedStatus == null || "全部".equals(normalizedStatus)) {
            return true;
        }
        return normalizedStatus.equals(normalizeText(application.get("applicationStatus")));
    }

    private Map<String, Object> withDisplayStallEventStatus(Map<String, Object> event) {
        Map<String, Object> response = new LinkedHashMap<>(event);
        response.put("status", displayStallEventStatus(event));
        response.put("statusNote", displayRegistrationProgress(event));
        return response;
    }

    private boolean matchesStallEventStatus(Map<String, Object> event, String status) {
        String normalizedStatus = normalizeText(status);
        if (normalizedStatus == null
                || "全部".equals(normalizedStatus)
                || "全部狀態".equals(normalizedStatus)) {
            return true;
        }
        return normalizedStatus.toUpperCase().equals(statusText(event.get("workflowStatus")))
                || normalizedStatus.equals(normalizeText(event.get("status")));
    }

    private Map<String, Object> withDisplayEquipmentEventStatus(Map<String, Object> event) {
        Map<String, Object> response = new LinkedHashMap<>(event);
        response.put("status", displayStallEventStatus(event));
        response.put("statusNote", displayRegistrationProgress(event));
        return response;
    }

    private boolean matchesEquipmentEventStatus(Map<String, Object> event, String status) {
        return matchesStallEventStatus(event, status);
    }

    private Map<String, Object> toAccountingSummaryResponse(Map<String, Object> account) {
        Object paidStallCount = account.get("paidStallCount");
        Object totalStallCount = account.get("totalStallCount");
        return orderedMap(
                "eventId", account.get("eventId"),
                "eventTitle", account.get("eventTitle"),
                "coverImageUrl", account.get("coverImageUrl"),
                "publishStatus", account.get("publishStatus"),
                "publishStatusText", account.get("publishStatusText"),
                "statusNote", account.get("statusNote"),
                "eventDate", formatEventDate(account),
                "paidStallCount", paidStallCount,
                "totalStallCount", totalStallCount,
                "paidStallText", integerText(paidStallCount) + " / " + integerText(totalStallCount),
                "grossRevenue", account.get("grossRevenue"),
                "refundAmount", account.get("refundAmount"),
                "returnedDepositAmount", account.get("returnedDepositAmount"),
                "unreturnedDepositAmount", account.get("unreturnedDepositAmount"),
                "netRevenue", account.get("netRevenue"));
    }

    private Map<String, Object> toAccountingEventResponse(Map<String, Object> account) {
        return orderedMap(
                "eventId", account.get("eventId"),
                "coverImageUrl", account.get("coverImageUrl"),
                "eventTitle", account.get("eventTitle"),
                "publishStatus", account.get("publishStatus"),
                "publishStatusText", account.get("publishStatusText"),
                "statusNote", account.get("statusNote"),
                "eventDate", formatEventDate(account),
                "locationName", account.get("locationName"),
                "address", joinAddress(
                        account.get("city"),
                        account.get("district"),
                        account.get("address")),
                "totalStallCount", account.get("totalStallCount"),
                "paidStallCount", account.get("paidStallCount"));
    }

    private Map<String, Object> toAccountingFinancialSummary(Map<String, Object> account) {
        return orderedMap(
                "grossRevenue", account.get("grossRevenue"),
                "refundAmount", account.get("refundAmount"),
                "returnedDepositAmount", account.get("returnedDepositAmount"),
                "unreturnedDepositAmount", account.get("unreturnedDepositAmount"),
                "netRevenue", account.get("netRevenue"));
    }

    private Map<String, Object> toAccountingStatistics(Map<String, Object> account) {
        return orderedMap(
                "payment", orderedMap(
                        "totalStallCount", account.get("totalStallCount"),
                        "paidStallCount", account.get("paidStallCount"),
                        "pendingPaymentStallCount", account.get("pendingPaymentStallCount")),
                "refund", orderedMap(
                        "refundCount", account.get("refundCount"),
                        "refundedCount", account.get("refundedCount"),
                        "refundingCount", account.get("refundingCount")),
                "deposit", orderedMap(
                        "returnedDepositCount", account.get("returnedDepositCount"),
                        "returnedDepositAmount", account.get("returnedDepositAmount"),
                        "unreturnedDepositCount", account.get("unreturnedDepositCount"),
                        "unreturnedDepositAmount", account.get("unreturnedDepositAmount")));
    }

    private Map<String, Object> toAccountingPaymentDetailResponse(Map<String, Object> payment) {
        return orderedMap(
                "applicationId", payment.get("applicationId"),
                "paymentNo", payment.get("paymentNo"),
                "brandName", payment.get("brandName"),
                "contactName", payment.get("contactName"),
                "paidAt", formatDateTime(firstPresent(payment.get("paidAt"), payment.get("paymentCreatedAt"))),
                "paymentAmount", payment.get("paymentAmount"),
                "refundAmount", payment.get("refundAmount"),
                "depositStatus", displayDepositStatus(payment.get("depositStatus")),
                "accountingStatus", displayAccountingStatus(payment));
    }

    private boolean matchesAccountingStatus(Map<String, Object> payment, String status) {
        String normalizedStatus = normalizeText(status);
        if (normalizedStatus == null
                || "全部".equals(normalizedStatus)
                || "全部狀態".equals(normalizedStatus)) {
            return true;
        }
        return normalizedStatus.equals(normalizeText(payment.get("accountingStatus")));
    }

    private String displayAccountingStatus(Map<String, Object> payment) {
        if (isTrue(payment.get("isCancelled"))) {
            return "已取消";
        }
        String refundStatus = statusText(payment.get("refundStatus"));
        if ("REFUNDED".equals(refundStatus)) {
            return "已退款";
        }
        if ("REFUNDING".equals(refundStatus)) {
            return "退款處理中";
        }
        if ("REFUND_REQUESTED".equals(refundStatus)) {
            return "退款申請中";
        }
        if ("PAID".equals(statusText(payment.get("paymentStatus")))) {
            return "付款成功";
        }
        return displayPaymentRecordStatus(payment.get("paymentStatus"));
    }

    private String displayPaymentRecordStatus(Object value) {
        return switch (statusText(value) == null ? "" : statusText(value)) {
            case "PAID" -> "付款成功";
            case "PENDING" -> "待付款";
            case "FAILED" -> "付款失敗";
            case "EXPIRED" -> "付款逾期";
            default -> normalizeText(value);
        };
    }

    private String displayDepositStatus(Object value) {
        return switch (statusText(value) == null ? "" : statusText(value)) {
            case "RETURNED" -> "已退還";
            case "NOT_RETURNED" -> "未退還";
            default -> normalizeText(value);
        };
    }

    private Map<String, Object> toStallEventSummaryResponse(Map<String, Object> event) {
        return orderedMap(
                "eventId", event.get("eventId"),
                "eventTitle", event.get("eventTitle"),
                "coverImageUrl", event.get("coverImageUrl"),
                "eventDate", formatEventDate(event),
                "address", joinAddress(
                        event.get("city"),
                        event.get("district"),
                        event.get("locationName")),
                "totalStallCount", event.get("totalStallCount"),
                "availableStallCount", event.get("availableStallCount"),
                "selectedStallCount", event.get("selectedStallCount"),
                "status", event.get("status"),
                "statusNote", event.get("statusNote"));
    }

    private Map<String, Object> toEquipmentSummaryResponse(Map<String, Object> event) {
        return orderedMap(
                "eventId", event.get("eventId"),
                "eventTitle", event.get("eventTitle"),
                "coverImageUrl", event.get("coverImageUrl"),
                "eventDate", formatEventDate(event),
                "status", event.get("status"),
                "statusNote", event.get("statusNote"),
                "registeredStallCount", event.get("registeredStallCount"),
                "freeEquipmentRentalCount", event.get("freeEquipmentRentalCount"),
                "paidEquipmentRentalCount", event.get("paidEquipmentRentalCount"),
                "freePowerRentalCount", event.get("freePowerRentalCount"),
                "paidExtraPowerRentalCount", event.get("paidExtraPowerRentalCount"),
                "vehicleRegistrationCount", event.get("vehicleRegistrationCount"));
    }

    private Map<String, Object> toEquipmentDetailEventResponse(Map<String, Object> event) {
        return orderedMap(
                "eventId", event.get("eventId"),
                "eventTitle", event.get("eventTitle"),
                "coverImageUrl", event.get("coverImageUrl"),
                "status", event.get("status"),
                "statusNote", event.get("statusNote"),
                "eventTime", formatEventDateTimeRange(event.get("eventStartAt"), event.get("eventEndAt")),
                "locationName", event.get("locationName"),
                "address", joinAddress(
                        event.get("city"),
                        event.get("district"),
                        event.get("address")));
    }

    private Map<String, Object> toEquipmentOverview(
            List<Map<String, Object>> rentalStats,
            List<Map<String, Object>> applicationRows) {
        return orderedMap(
                "registeredStallCount", applicationRows.size(),
                "basicEquipmentCount", sumRentedQuantity(rentalStats, "EQUIPMENT", "FREE"),
                "basicPowerCount", sumRentedQuantity(rentalStats, "POWER", "FREE"),
                "equipmentRentalCount", sumRentedQuantity(rentalStats, "EQUIPMENT", "PAID"),
                "extraPowerCount", sumRentedQuantity(rentalStats, "POWER", "PAID"),
                "vehicleRegistrationCount", applicationRows.stream()
                        .filter(row -> normalizeText(row.get("vehicleNo")) != null)
                        .count());
    }

    private int sumRentedQuantity(List<Map<String, Object>> rows, String itemType, String chargeType) {
        return rows.stream()
                .filter(row -> itemType.equals(statusText(row.get("itemType"))))
                .filter(row -> chargeType.equals(statusText(row.get("chargeType"))))
                .mapToInt(row -> intValue(row.get("rentedQuantity")))
                .sum();
    }

    private List<Map<String, Object>> toEquipmentColumns(List<Map<String, Object>> equipments) {
        return equipments.stream()
                .filter(this::isActiveEquipment)
                .map(row -> orderedMap(
                        "eventEquipmentId", row.get("eventEquipmentId"),
                        "equipmentName", row.get("equipmentName")))
                .toList();
    }

    private List<Map<String, Object>> toEventEquipmentRows(List<Map<String, Object>> equipments) {
        Map<String, Map<String, Object>> rowsByGroup = new LinkedHashMap<>();
        for (Map<String, Object> row : equipments) {
            if (!"EQUIPMENT".equals(statusText(row.get("itemType")))) {
                continue;
            }

            Map<String, Object> groupedRow = rowsByGroup.computeIfAbsent(
                    eventEquipmentGroupKey(row),
                    key -> orderedMap(
                            "eventEquipmentId", null,
                            "equipmentGroupKey", row.get("equipmentGroupKey"),
                            "itemType", row.get("itemType"),
                            "equipmentName", row.get("equipmentName"),
                            "description", row.get("equipmentDescription"),
                            "unit", row.get("unit"),
                            "freeEventEquipmentId", null,
                            "paidEventEquipmentId", null,
                            "freeProvided", null,
                            "paidRentalFee", null,
                            "pricingUnit", null,
                            "rentalStatus", null,
                            "perStallRentalLimit", null,
                            "dailyRentableQuantity", null));

            if (groupedRow.get("eventEquipmentId") == null) {
                groupedRow.put("eventEquipmentId", row.get("eventEquipmentId"));
            }
            if (groupedRow.get("description") == null && row.get("equipmentDescription") != null) {
                groupedRow.put("description", row.get("equipmentDescription"));
            }

            String chargeType = statusText(row.get("chargeType"));
            if ("FREE".equals(chargeType)) {
                groupedRow.put("freeEventEquipmentId", row.get("eventEquipmentId"));
                groupedRow.put("freeProvided", row.get("perStallRentalLimit"));
                groupedRow.put("dailyRentableQuantity", sumNullableInts(
                        groupedRow.get("dailyRentableQuantity"),
                        row.get("stockQuantity")));
                if (groupedRow.get("rentalStatus") == null) {
                    groupedRow.put("rentalStatus", row.get("rentalStatus"));
                }
                continue;
            }

            if ("PAID".equals(chargeType)) {
                groupedRow.put("eventEquipmentId", row.get("eventEquipmentId"));
                groupedRow.put("paidEventEquipmentId", row.get("eventEquipmentId"));
                groupedRow.put("paidRentalFee", row.get("rentalFee"));
                groupedRow.put("pricingUnit", row.get("pricingUnit"));
                groupedRow.put("rentalStatus", row.get("rentalStatus"));
                groupedRow.put("perStallRentalLimit", row.get("perStallRentalLimit"));
                groupedRow.put("dailyRentableQuantity", sumNullableInts(
                        groupedRow.get("dailyRentableQuantity"),
                        row.get("stockQuantity")));
            }
        }

        return List.copyOf(rowsByGroup.values());
    }

    private String eventEquipmentGroupKey(Map<String, Object> row) {
        String itemType = statusText(row.get("itemType"));
        String groupKey = normalizeText(row.get("equipmentGroupKey"));
        if (groupKey != null) {
            return itemType + "|" + groupKey;
        }
        return itemType + "|"
                + normalizeText(row.get("equipmentName")) + "|"
                + normalizeText(row.get("unit"));
    }

    private List<Map<String, Object>> toBasicPowerRows(List<Map<String, Object>> equipments) {
        return equipments.stream()
                .filter(row -> "POWER".equals(statusText(row.get("itemType"))))
                .filter(row -> "FREE".equals(statusText(row.get("chargeType"))))
                .map(row -> orderedMap(
                        "eventEquipmentId", row.get("eventEquipmentId"),
                        "equipmentName", row.get("equipmentName"),
                        "voltageType", voltageType(row),
                        "freeWattage", wattageWithUnit(row.get("wattageLimit"))))
                .toList();
    }

    private List<Map<String, Object>> toExtraPowerRows(List<Map<String, Object>> equipments) {
        return equipments.stream()
                .filter(row -> "POWER".equals(statusText(row.get("itemType"))))
                .filter(row -> "PAID".equals(statusText(row.get("chargeType"))))
                .map(row -> orderedMap(
                        "eventEquipmentId", row.get("eventEquipmentId"),
                        "equipmentName", row.get("equipmentName"),
                        "powerPlan", powerPlan(row),
                        "perStallProvidedQuantity", row.get("perStallRentalLimit"),
                        "availableGroupQuantity", row.get("stockQuantity")))
                .toList();
    }

    private List<Map<String, Object>> toEquipmentRentalStatistics(List<Map<String, Object>> stats) {
        Map<String, Map<String, Object>> rowsByGroup = new LinkedHashMap<>();
        Map<String, Integer> rentedByGroup = new LinkedHashMap<>();
        Map<String, Integer> stockByGroup = new LinkedHashMap<>();
        Set<String> groupsWithStock = new LinkedHashSet<>();

        for (Map<String, Object> row : stats) {
            if (!"EQUIPMENT".equals(statusText(row.get("itemType")))) {
                continue;
            }

            String groupKey = eventEquipmentGroupKey(row);
            rowsByGroup.computeIfAbsent(
                    groupKey,
                    key -> orderedMap(
                            "eventEquipmentId", row.get("eventEquipmentId"),
                            "equipmentGroupKey", row.get("equipmentGroupKey"),
                            "equipmentName", row.get("equipmentName")));

            rentedByGroup.merge(groupKey, intValue(row.get("rentedQuantity")), Integer::sum);
            Integer stock = nullableInt(row.get("stockQuantity"));
            if (stock != null) {
                stockByGroup.merge(groupKey, stock, Integer::sum);
                groupsWithStock.add(groupKey);
            }
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        int totalRented = 0;
        int totalStock = 0;
        boolean hasTotalStock = false;

        for (Map.Entry<String, Map<String, Object>> entry : rowsByGroup.entrySet()) {
            String groupKey = entry.getKey();
            Map<String, Object> row = entry.getValue();
            int rented = rentedByGroup.getOrDefault(groupKey, 0);
            Integer stock = groupsWithStock.contains(groupKey) ? stockByGroup.getOrDefault(groupKey, 0) : null;
            Integer remaining = stock == null ? null : Math.max(stock - rented, 0);
            rows.add(orderedMap(
                    "eventEquipmentId", row.get("eventEquipmentId"),
                    "equipmentGroupKey", row.get("equipmentGroupKey"),
                    "equipmentName", row.get("equipmentName"),
                    "rentedQuantity", rented,
                    "rentableQuantity", stock,
                    "rentedText", stock == null ? String.valueOf(rented) : rented + " / " + stock,
                    "remainingQuantity", remaining));
            totalRented += rented;
            if (stock != null) {
                totalStock += stock;
                hasTotalStock = true;
            }
        }
        rows.add(orderedMap(
                "eventEquipmentId", null,
                "equipmentGroupKey", null,
                "equipmentName", "\u7e3d\u8a08",
                "rentedQuantity", totalRented,
                "rentableQuantity", hasTotalStock ? totalStock : null,
                "rentedText", hasTotalStock ? totalRented + " / " + totalStock : String.valueOf(totalRented),
                "remainingQuantity", hasTotalStock ? Math.max(totalStock - totalRented, 0) : null,
                "isTotal", true));
        return rows;
    }

    private List<Map<String, Object>> toExtraPowerApplicationStatistics(List<Map<String, Object>> stats) {
        List<Map<String, Object>> rows = new ArrayList<>();
        int totalQuantity = 0;
        for (Map<String, Object> row : stats) {
            if (!"POWER".equals(statusText(row.get("itemType")))
                    || !"PAID".equals(statusText(row.get("chargeType")))) {
                continue;
            }
            int quantity = intValue(row.get("rentedQuantity"));
            rows.add(orderedMap(
                    "eventEquipmentId", row.get("eventEquipmentId"),
                    "equipmentName", row.get("equipmentName"),
                    "powerPlan", powerPlan(row),
                    "applicationQuantity", quantity));
            totalQuantity += quantity;
        }
        rows.add(orderedMap(
                "eventEquipmentId", null,
                "equipmentName", "\u7e3d\u8a08",
                "powerPlan", "\u7e3d\u8a08",
                "applicationQuantity", totalQuantity,
                "isTotal", true));
        return rows;
    }

    private Map<String, Object> toVehicleRegistrationStatistics(List<Map<String, Object>> rows) {
        long registered = rows.stream()
                .filter(row -> normalizeText(row.get("vehicleNo")) != null)
                .count();
        long unregistered = rows.size() - registered;
        return orderedMap(
                "stallCount", rows.size(),
                "registeredCount", registered,
                "unregisteredCount", unregistered);
    }

    private Map<String, Object> toEquipmentRentalManagement(
            List<Map<String, Object>> applicationRows,
            List<Map<String, Object>> rentalRows,
            List<Map<String, Object>> equipments) {
        Map<String, Map<String, Object>> rowsByApplication = baseManagementRows(applicationRows);
        Map<String, Object> zeroQuantities = new LinkedHashMap<>();
        Map<String, String> equipmentNamesById = new LinkedHashMap<>();
        for (Map<String, Object> equipment : equipments) {
            if (isActiveEquipment(equipment)) {
                String equipmentId = String.valueOf(equipment.get("eventEquipmentId"));
                String equipmentName = normalizeText(equipment.get("equipmentName"));
                equipmentNamesById.put(equipmentId, equipmentName);
                zeroQuantities.put(equipmentName, 0);
            }
        }
        rowsByApplication.values().forEach(row -> row.put("equipmentQuantities", new LinkedHashMap<>(zeroQuantities)));

        for (Map<String, Object> rental : rentalRows) {
            String applicationId = String.valueOf(rental.get("applicationId"));
            Map<String, Object> row = rowsByApplication.get(applicationId);
            if (row == null) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> quantities = (Map<String, Object>) row.get("equipmentQuantities");
            String equipmentId = String.valueOf(rental.get("eventEquipmentId"));
            String equipmentName = equipmentNamesById.get(equipmentId);
            if (equipmentName != null) {
                quantities.put(equipmentName, intValue(quantities.get(equipmentName)) + intValue(rental.get("quantity")));
            }
        }
        List<Map<String, Object>> items = List.copyOf(rowsByApplication.values());
        return orderedMap(
                "totalCount", items.size(),
                "items", items);
    }

    private Map<String, Object> toExtraPowerManagement(
            List<Map<String, Object>> applicationRows,
            List<Map<String, Object>> powerRows) {
        Map<String, Map<String, Object>> rowsByApplication = baseManagementRows(applicationRows);
        Map<String, List<String>> plansByApplication = new LinkedHashMap<>();
        for (Map<String, Object> row : powerRows) {
            String applicationId = String.valueOf(row.get("applicationId"));
            String plan = powerPlan(row);
            int quantity = intValue(row.get("quantity"));
            plansByApplication.computeIfAbsent(applicationId, key -> new ArrayList<>())
                    .add(quantity > 1 ? plan + " x" + quantity : plan);
        }
        rowsByApplication.forEach((applicationId, row) ->
                row.put("powerPlan", String.join("\u3001", plansByApplication.getOrDefault(applicationId, List.of()))));
        List<Map<String, Object>> items = List.copyOf(rowsByApplication.values());
        return orderedMap(
                "totalCount", items.size(),
                "items", items);
    }

    private Map<String, Object> toVehicleManagement(List<Map<String, Object>> rows) {
        List<Map<String, Object>> items = rows.stream()
                .filter(row -> normalizeText(row.get("vehicleNo")) != null)
                .map(row -> orderedMap(
                        "applicationId", row.get("applicationId"),
                        "stallNo", row.get("stallNo"),
                        "brandName", row.get("brandName"),
                        "contactName", row.get("contactName"),
                        "vehicleNo", row.get("vehicleNo")))
                .toList();
        return orderedMap(
                "totalCount", items.size(),
                "items", items);
    }

    private Map<String, Map<String, Object>> baseManagementRows(List<Map<String, Object>> rows) {
        Map<String, Map<String, Object>> rowsByApplication = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            rowsByApplication.put(String.valueOf(row.get("applicationId")), orderedMap(
                    "applicationId", row.get("applicationId"),
                    "stallNo", row.get("stallNo"),
                    "brandName", row.get("brandName")));
        }
        return rowsByApplication;
    }

    private Map<String, Object> toApplicationSummaryResponse(
            Map<String, Object> application,
            Map<Long, CategoryResponse> categoryByVendorProfileId) {
        return orderedMap(
                "applicationId", application.get("applicationId"),
                "eventTitle", application.get("eventTitle"),
                "eventTime", application.get("eventTime"),
                "vendorName", application.get("vendorName"),
                "category", categoryByVendorProfileId.get(toLong(application.get("vendorProfileId"))),
                "vendorOwnerName", application.get("vendorOwnerName"),
                "appliedAt", formatAppliedAt(application),
                "applicationStatus", application.get("applicationStatus"));
    }

    private Map<String, Object> toApplicationDetailResponse(
            Map<String, Object> application,
            List<Map<String, Object>> applicationDateRows,
            List<Map<String, Object>> equipmentRentalRows) {
        Map<String, Object> response = new LinkedHashMap<>();
        String workflowStatus = statusText(application.get("workflowStatus"));
        response.put("application", orderedMap(
                "applicationId", application.get("applicationId"),
                "applicationNo", application.get("applicationNo"),
                "applicationStatus", application.get("applicationStatus")));

        response.put("event", orderedMap(
                "eventId", application.get("eventId"),
                "eventCoverImageUrl", application.get("eventCoverImageUrl"),
                "eventTitle", application.get("eventTitle"),
                "workflowStatus", workflowStatus,
                "unpublishRequested", WorkflowStatus.UNPUBLISH_REQUESTED.name().equals(workflowStatus),
                "unpublished", WorkflowStatus.UNPUBLISHED.name().equals(workflowStatus),
                "eventStatus", displayEventStatus(application),
                "statusNote", displayRegistrationProgress(application),
                "eventTime", formatEventDate(application),
                "eventStartAt", application.get("eventStartAt"),
                "eventEndAt", application.get("eventEndAt"),
                "locationName", joinAddress(
                        application.get("eventCity"),
                        application.get("eventDistrict"),
                        application.get("locationName")),
                "address", joinAddress(
                        application.get("eventCity"),
                        application.get("eventDistrict"),
                        application.get("eventAddress"))));

        response.put("vendor", orderedMap(
                "vendorOwnerName", application.get("vendorOwnerName"),
                "vendorPhone", application.get("vendorPhone"),
                "vendorEmail", application.get("vendorContactEmail"),
                "address", joinAddress(
                        application.get("vendorCity"),
                        application.get("vendorDistrict"),
                        application.get("vendorAddress"))));

        response.put("brand", orderedMap(
                "brandId", application.get("vendorProfileId"),
                "brandName", application.get("vendorName"),
                "avatarImageUrl", application.get("vendorAvatarUrl"),
                "category", vendorCategory(toLong(application.get("vendorProfileId"))),
                "brandDescription", application.get("brandDescription")));

        response.put("applicationdetail", orderedMap(
                "registrationPeriods", toRegistrationPeriods(application, applicationDateRows),
                "width", application.get("stallWidth"),
                "length", application.get("stallLength"),
                "stallZone", application.get("stallZoneName"),
                "vehicleNo", application.get("vehicleNo"),
                "applicantNote", application.get("applicantNote"),
                "reviewNote", application.get("reviewNote"),
                "reviewNoteDetail", application.get("reviewNoteDetail")));

        response.put("stall", toStallResponses(applicationDateRows));

        Object baseFee = application.get("baseFee");
        Object depositAmount = application.get("depositAmount");
        Object totalAmount = application.get("totalAmount");
        BigDecimal equipmentRentalFee = sumEquipmentRentalFee(equipmentRentalRows, "EQUIPMENT");
        BigDecimal extraPowerFee = sumEquipmentRentalFee(equipmentRentalRows, "POWER");
        Integer applicationDays = applicationDateRows.isEmpty()
                ? applicationDays(application.get("applyDates"))
                : applicationDateRows.size();
        BigDecimal applicationFee = multiply(baseFee, applicationDays);
        response.put("fee", orderedMap(
                "paymentStatus", displayPaymentStatus(application),
                "paymentMethod", application.get("paymentProvider"),
                "paymentNo", application.get("paymentNo"),
                "providerTradeNo", application.get("paymentProviderTradeNo"),
                "paidAt", application.get("paidAt"),
                "paymentAmount", firstPresent(application.get("paymentAmount"), totalAmount)));
        response.put("refund", orderedMap(
                "refundStatus", application.get("refundStatus"),
                "refundStatusText", displayRefundStatus(application.get("refundStatus")),
                "refundMethod", application.get("paymentProvider"),
                "refundNo", application.get("refundNo"),
                "refundAmount", application.get("refundAmount"),
                "refundedAt", application.get("refundedAt")));
        response.put("feedetail", toFeeDetail(
                baseFee,
                applicationDays,
                applicationDateRows,
                toEquipmentRentalResponses(equipmentRentalRows),
                applicationFee,
                equipmentRentalFee,
                extraPowerFee,
                depositAmount,
                totalAmount));
        response.put("equipmentRentals", toEquipmentRentalGroups(
                toEquipmentRentalResponses(equipmentRentalRows),
                applicationDays));

        return response;
    }

    private CategoryResponse vendorCategory(Long vendorProfileId) {
        if (vendorProfileId == null) {
            return null;
        }
        return categoryByVendorProfileId(List.of(Map.of("vendorProfileId", vendorProfileId))).get(vendorProfileId);
    }

    private Map<Long, CategoryResponse> categoryByVendorProfileId(List<Map<String, Object>> rows) {
        List<Long> ids = rows.stream()
                .map(row -> toLong(row.get("vendorProfileId")))
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, CategoryResponse> result = new LinkedHashMap<>();
        if (ids.isEmpty()) {
            return result;
        }
        for (Map<String, Object> row : organizerRepository.findVendorCategoriesByProfileIds(ids)) {
            Long profileId = toLong(row.get("vendorProfileId"));
            result.put(profileId, new CategoryResponse(
                    toLong(row.get("id")), Objects.toString(row.get("name"), ""),
                    Objects.toString(row.get("slug"), "")));
        }
        return result;
    }

    private String displayEventStatus(Map<String, Object> application) {
        LocalDateTime startAt = toLocalDateTime(application.get("eventStartAt"));
        LocalDateTime endAt = toLocalDateTime(application.get("eventEndAt"));
        LocalDateTime now = LocalDateTime.now();
        if (endAt != null && now.isAfter(endAt)) {
            return "已結束";
        }
        if (startAt != null && !now.isBefore(startAt) && (endAt == null || !now.isAfter(endAt))) {
            return "進行中";
        }
        if (startAt != null && !now.toLocalDate().isBefore(startAt.toLocalDate().minusDays(7))) {
            return "即將開始";
        }
        return "活動預告";
    }

    private String displayStallEventStatus(Map<String, Object> event) {
        String workflowStatus = statusText(event.get("workflowStatus"));
        if ("UNPUBLISHED".equals(workflowStatus) || "CANCELLED".equals(workflowStatus)) {
            return "已下架";
        }
        if ("READY_TO_PUBLISH".equals(workflowStatus)) {
            return "待發布";
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime registrationStartAt = toLocalDateTime(event.get("registrationStartAt"));
        LocalDateTime registrationEndAt = toLocalDateTime(event.get("registrationEndAt"));
        LocalDateTime brandsPublicAt = toLocalDateTime(event.get("brandsPublicAt"));
        LocalDateTime startAt = toLocalDateTime(event.get("eventStartAt"));
        LocalDateTime endAt = toLocalDateTime(event.get("eventEndAt"));

        if (endAt != null && now.isAfter(endAt)) {
            return "已結束";
        }
        if (startAt != null && !now.isBefore(startAt) && (endAt == null || !now.isAfter(endAt))) {
            return "進行中";
        }
        if (registrationStartAt != null && now.isBefore(registrationStartAt)) {
            return "待發布";
        }
        if (registrationEndAt != null && !now.isAfter(registrationEndAt)) {
            return isStallEventFull(event) ? "已額滿" : "報名中";
        }
        if (brandsPublicAt == null || !now.isBefore(brandsPublicAt)) {
            return "品牌已公開";
        }
        return "品牌已公開";
    }

    private String displayRegistrationProgress(Map<String, Object> event) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime registrationStartAt = toLocalDateTime(event.get("registrationStartAt"));
        LocalDateTime registrationEndAt = toLocalDateTime(event.get("registrationEndAt"));

        if (registrationStartAt != null && now.isBefore(registrationStartAt)) {
            return "\u672a\u958b\u59cb\u5831\u540d";
        }
        if (registrationEndAt != null && now.isAfter(registrationEndAt)) {
            return "\u5831\u540d\u622a\u6b62";
        }
        return "\u5831\u540d\u4e2d";
    }

    private boolean isStallEventFull(Map<String, Object> event) {
        return isTrue(event.get("isFullySelected"));
    }

    private String toRegistrationPeriods(
            Map<String, Object> application,
            List<Map<String, Object>> applicationDateRows) {
        return applicationDateRows.stream()
                .map(row -> registrationPeriodText(row.get("applyDate"), application))
                .filter(period -> period != null && !period.isBlank())
                .reduce((left, right) -> left + " - " + right)
                .orElse(null);
    }

    private String registrationPeriodText(Object applyDate, Map<String, Object> application) {
        String date = formatDate(applyDate);
        String startTime = formatTime(application.get("eventStartAt"));
        String endTime = formatTime(application.get("eventEndAt"));
        if (date == null || startTime == null || endTime == null) {
            return null;
        }
        return date + " " + startTime + "-" + endTime;
    }

    private List<Map<String, Object>> toStallResponses(List<Map<String, Object>> applicationDateRows) {
        return applicationDateRows.stream()
                .map(row -> orderedMap(
                        "applyDate", formatDate(row.get("applyDate")),
                        "stallNo", row.get("stallNo"),
                        "zoneName", row.get("zoneName"),
                        "selectionStatus", row.get("selectedStallId") == null ? "未選擇" : "已選擇"))
                .toList();
    }

    private List<Map<String, Object>> toFeeDetail(
            Object baseFee,
            Integer applicationDays,
            List<Map<String, Object>> applicationDateRows,
            List<Map<String, Object>> rentalRows,
            BigDecimal applicationFee,
            BigDecimal equipmentRentalFee,
            BigDecimal extraPowerFee,
            Object depositAmount,
            Object totalAmount) {
        List<Map<String, Object>> details = new ArrayList<>();
        details.add(orderedMap(
                "item", "報名費",
                "content", applicationFeeContent(applicationDays, applicationDateRows),
                "amount", applicationFee));
        details.add(orderedMap(
                "item", "設備租借費",
                "content", equipmentFeeContent(rentalRows),
                "amount", zeroIfNull(equipmentRentalFee)));
        details.add(orderedMap(
                "item", "額外電費",
                "content", powerFeeContent(rentalRows),
                "amount", zeroIfNull(extraPowerFee)));
        details.add(orderedMap(
                "item", "保證金",
                "content", "保證金",
                "amount", depositAmount));
        details.add(orderedMap(
                "item", "總計",
                "content", null,
                "amount", totalAmount));
        return details;
    }

    private Map<String, Object> toEquipmentRentalGroups(
            List<Map<String, Object>> rentalRows,
            Integer applicationDays) {
        List<Map<String, Object>> freeEquipmentRows = rentalRows.stream()
                .filter(row -> "FREE".equals(statusText(row.get("chargeType"))))
                .filter(row -> "EQUIPMENT".equals(statusText(row.get("itemType"))))
                .toList();
        List<Map<String, Object>> freePowerRows = rentalRows.stream()
                .filter(row -> "FREE".equals(statusText(row.get("chargeType"))))
                .filter(row -> "POWER".equals(statusText(row.get("itemType"))))
                .toList();
        return orderedMap(
                "freeEquipments", freeEquipmentRows.stream()
                        .filter(row -> "FREE".equals(statusText(row.get("chargeType"))))
                        .filter(row -> "EQUIPMENT".equals(statusText(row.get("itemType"))))
                        .map(row -> toFreeEquipmentResponse(row, applicationDays))
                        .toList(),
                "freeBasicPower", freePowerRows.stream()
                        .filter(row -> "FREE".equals(statusText(row.get("chargeType"))))
                        .filter(row -> "POWER".equals(statusText(row.get("itemType"))))
                        .map(this::toFreePowerResponse)
                        .toList(),
                "rentalEquipments", rentalRows.stream()
                        .filter(row -> "PAID".equals(statusText(row.get("chargeType"))))
                        .filter(row -> "EQUIPMENT".equals(statusText(row.get("itemType"))))
                        .map(row -> toPaidEquipmentResponse(row, applicationDays))
                        .toList(),
                "extraPower", rentalRows.stream()
                        .filter(row -> "PAID".equals(statusText(row.get("chargeType"))))
                        .filter(row -> "POWER".equals(statusText(row.get("itemType"))))
                        .map(row -> toPaidPowerResponse(row, applicationDays))
                        .toList());
    }

    private Map<String, Object> toFreeEquipmentResponse(Map<String, Object> row, Integer applicationDays) {
        return orderedMap(
                "equipmentName", row.get("equipmentName"),
                "specification", row.get("equipmentDescription"),
                "quantity", firstPresent(row.get("quantity"), row.get("stockQuantity")),
                "unit", "個",
                "subtotal", BigDecimal.ZERO);
    }

    private Map<String, Object> toPaidEquipmentResponse(Map<String, Object> row, Integer applicationDays) {
        return orderedMap(
                "equipmentName", row.get("equipmentName"),
                "specification", row.get("equipmentDescription"),
                "quantity", row.get("quantity"),
                "unit", quantityUnit(row),
                "subtotal", row.get("subtotal"),
                "subtotalContent", unitContent("共", applicationDays, "天"),
                "total", row.get("subtotal"));
    }

    private Map<String, Object> toFreePowerResponse(Map<String, Object> row) {
        return orderedMap(
                "powerSpecification", powerSpecification(row),
                "wattage", row.get("wattageLimit"),
                "unitPrice", row.get("rentalFee"),
                "subtotal", BigDecimal.ZERO);
    }

    private Map<String, Object> toPaidPowerResponse(Map<String, Object> row, Integer applicationDays) {
        return orderedMap(
                "powerSpecification", powerSpecification(row),
                "wattage", firstPresent(row.get("totalWattage"), row.get("wattageLimit")),
                "unitPrice", row.get("rentalFee"),
                "unit", pricingUnitText(row.get("pricingUnit")),
                "subtotal", row.get("subtotal"),
                "subtotalContent", unitContent("共", applicationDays, "天"),
                "total", row.get("subtotal"));
    }

    private String powerSpecification(Map<String, Object> row) {
        String description = normalizeText(row.get("equipmentDescription"));
        if (description != null) {
            return description;
        }
        String wattage = integerText(firstPresent(row.get("wattageLimit"), row.get("totalWattage")));
        return wattage == null ? null : wattage + "W";
    }

    private String applicationFeeContent(Integer applicationDays, List<Map<String, Object>> applicationDateRows) {
        String dateText = applicationDateRows.stream()
                .map(row -> formatDate(row.get("applyDate")))
                .filter(date -> date != null && !date.isBlank())
                .reduce((left, right) -> left + "、" + right)
                .orElse(null);
        String dayText = unitContent("", applicationDays, "天");
        if (dayText == null) {
            return dateText;
        }
        return dateText == null ? dayText : dayText + " (" + dateText + ")";
    }

    private String equipmentFeeContent(List<Map<String, Object>> rentalRows) {
        String content = rentalRows.stream()
                .filter(row -> "PAID".equals(statusText(row.get("chargeType"))))
                .filter(row -> "EQUIPMENT".equals(statusText(row.get("itemType"))))
                .map(this::equipmentContent)
                .filter(text -> text != null && !text.isBlank())
                .reduce((left, right) -> left + "、" + right)
                .orElse(null);
        return content == null ? "無租借設備" : content;
    }

    private String equipmentContent(Map<String, Object> row) {
        String equipmentName = normalizeText(row.get("equipmentName"));
        String quantity = integerText(row.get("quantity"));
        if (equipmentName == null) {
            return null;
        }
        return quantity == null ? equipmentName : equipmentName + "*" + quantity;
    }

    private String powerFeeContent(List<Map<String, Object>> rentalRows) {
        String content = rentalRows.stream()
                .filter(row -> "PAID".equals(statusText(row.get("chargeType"))))
                .filter(row -> "POWER".equals(statusText(row.get("itemType"))))
                .map(row -> {
                    String specification = powerSpecification(row);
                    String quantity = integerText(row.get("quantity"));
                    if (specification == null) {
                        return null;
                    }
                    return quantity == null ? specification : specification + "*" + quantity;
                })
                .filter(text -> text != null && !text.isBlank())
                .reduce((left, right) -> left + "、" + right)
                .orElse(null);
        return content == null ? "無額外申請用電" : content;
    }

    private String stallFeeNote(Map<String, Object> application) {
        String stallSpec = stallSpec(application);
        String dayLabel = dayLabel(applicationDays(application.get("applyDates")));
        if (stallSpec == null && dayLabel == null) {
            return null;
        }
        if (stallSpec == null) {
            return dayLabel;
        }
        if (dayLabel == null) {
            return stallSpec;
        }
        return stallSpec + " (" + dayLabel + ")";
    }

    private String stallSpec(Map<String, Object> application) {
        String width = decimalText(application.get("stallWidth"));
        String length = decimalText(application.get("stallLength"));
        if (width == null || length == null) {
            return null;
        }
        return width + " \u516c\u5c3a x " + length + " \u516c\u5c3a \u6524\u4f4d";
    }

    private String rentalFeeNote(List<Map<String, Object>> rows) {
        List<Map<String, Object>> rentals = toEquipmentRentalResponses(rows);
        if (rentals.isEmpty()) {
            return null;
        }
        return rentals.stream()
                .map(this::rentalNote)
                .filter(note -> note != null && !note.isBlank())
                .reduce((left, right) -> left + "\u3001" + right)
                .orElse(null);
    }

    private String rentalNote(Map<String, Object> rental) {
        String equipmentName = normalizeText(rental.get("equipmentName"));
        String fee = moneyText(rentalFeePerUnitPeriod(rental));
        String unit = pricingUnitText(rental.get("pricingUnit"));
        String rentalUnits = integerText(rental.get("rentalUnits"));
        if (equipmentName == null || fee == null || unit == null || rentalUnits == null) {
            return equipmentName;
        }
        return equipmentName + " " + fee + "/" + unit + " x " + rentalUnits + unit;
    }

    private BigDecimal rentalFeePerUnitPeriod(Map<String, Object> rental) {
        BigDecimal subtotal = toBigDecimal(rental.get("subtotal"));
        BigDecimal rentalUnits = toBigDecimal(rental.get("rentalUnits"));
        if (subtotal != null && rentalUnits != null && rentalUnits.compareTo(BigDecimal.ZERO) > 0) {
            return subtotal.divide(rentalUnits, 2, RoundingMode.HALF_UP);
        }
        return toBigDecimal(rental.get("rentalFee"));
    }

    private List<Map<String, Object>> toEquipmentRentalResponses(List<Map<String, Object>> rows) {
        Map<Long, Map<String, Object>> rentalsById = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Long rentalId = toLong(row.get("equipmentRentalId"));
            if (rentalId == null) {
                continue;
            }

            Map<String, Object> rental = rentalsById.computeIfAbsent(rentalId, id -> orderedMap(
                    "equipmentRentalId", id,
                    "eventEquipmentId", row.get("eventEquipmentId"),
                    "equipmentName", row.get("equipmentName"),
                    "equipmentDescription", row.get("equipmentDescription"),
                    "chargeType", row.get("chargeType"),
                    "itemType", row.get("itemType"),
                    "wattageLimit", row.get("wattageLimit"),
                    "rentalFee", row.get("rentalFee"),
                    "pricingUnit", row.get("pricingUnit"),
                    "quantity", row.get("quantity"),
                    "rentalUnits", row.get("rentalUnits"),
                    "subtotal", row.get("subtotal"),
                    "appliances", new ArrayList<Map<String, Object>>(),
                    "totalWattage", null));

            Long applianceId = toLong(row.get("applianceId"));
            if (applianceId == null) {
                continue;
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> appliances = (List<Map<String, Object>>) rental.get("appliances");
            appliances.add(orderedMap(
                    "applianceId", applianceId,
                    "applianceName", row.get("applianceName"),
                    "wattage", row.get("wattage")));

            BigDecimal wattage = toBigDecimal(row.get("wattage"));
            BigDecimal totalWattage = toBigDecimal(rental.get("totalWattage"));
            if (wattage != null) {
                rental.put("totalWattage", (totalWattage == null ? BigDecimal.ZERO : totalWattage).add(wattage));
            }
        }
        return new ArrayList<>(rentalsById.values());
    }

    private BigDecimal sumEquipmentRentalFee(List<Map<String, Object>> rows, String itemType) {
        Map<Long, BigDecimal> subtotalsByRentalId = new LinkedHashMap<>();
        String normalizedItemType = statusText(itemType);
        for (Map<String, Object> row : rows) {
            Long rentalId = toLong(row.get("equipmentRentalId"));
            BigDecimal subtotal = toBigDecimal(row.get("subtotal"));
            if (rentalId != null
                    && subtotal != null
                    && "PAID".equals(statusText(row.get("chargeType")))
                    && normalizedItemType.equals(statusText(row.get("itemType")))) {
                subtotalsByRentalId.putIfAbsent(rentalId, subtotal);
            }
        }
        return subtotalsByRentalId.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal sumEquipmentRentalFee(List<Map<String, Object>> rows) {
        Map<Long, BigDecimal> subtotalsByRentalId = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Long rentalId = toLong(row.get("equipmentRentalId"));
            BigDecimal subtotal = toBigDecimal(row.get("subtotal"));
            if (rentalId != null && subtotal != null) {
                subtotalsByRentalId.putIfAbsent(rentalId, subtotal);
            }
        }
        if (subtotalsByRentalId.isEmpty()) {
            return null;
        }
        return subtotalsByRentalId.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<Map<String, Object>> toApplicationStatusFlow(Map<String, Object> application) {
        List<Map<String, Object>> statusLogs = applicationStatusLogs(application);
        List<Map<String, Object>> flow = new ArrayList<>();
        String reviewStatus = statusText(application.get("reviewStatus"));
        String paymentStatus = statusText(application.get("paymentStatus"));
        String refundStatus = statusText(application.get("refundStatus"));
        boolean reviewReached = reviewStatus != null && !"PENDING".equals(reviewStatus);
        boolean cancelled = isTrue(application.get("isCancelled")) || "EXPIRED".equals(paymentStatus);
        boolean paymentReached = paymentStatus != null
                && !"PENDING".equals(paymentStatus)
                && !"EXPIRED".equals(paymentStatus)
                && reviewReached
                && (!cancelled || refundStatus != null);
        boolean refundRequestedReached = refundStatus != null;
        boolean refundReviewReached = "REFUNDING".equals(refundStatus)
                || "REFUND_FAILED".equals(refundStatus)
                || "REFUNDED".equals(refundStatus);
        boolean refundedReached = "REFUNDED".equals(refundStatus);
        boolean stallSelectedReached = isAllApplicationDatesSelected(application);
        boolean depositReturnedReached = "RETURNED".equals(statusText(application.get("depositStatus")));

        flow.add(statusStep(
                "APPLIED",
                "\u5831\u540d\u65e5\u671f",
                "\u5df2\u5831\u540d",
                application.get("appliedAt")));
        flow.add(statusStep(
                "REVIEW",
                "\u5be9\u6838\u6642\u9593",
                reviewReached ? displayReviewStatus(application.get("reviewStatus")) : null,
                reviewReached
                        ? statusCreatedAt(statusLogs, "event_applications.review_status", application.get("reviewStatus"))
                        : null));

        Object cancelledAt = firstPresent(
                statusCreatedAt(statusLogs, "event_applications.is_cancelled", application.get("isCancelled")),
                "EXPIRED".equals(paymentStatus)
                        ? statusCreatedAt(statusLogs, "event_applications.payment_status", "EXPIRED")
                        : null);
        flow.add(statusStep(
                "CANCELLED",
                "\u53d6\u6d88\u6642\u9593",
                cancelled ? "\u5df2\u53d6\u6d88" : null,
                cancelled ? cancelledAt : null));

        Object paymentCreatedAt = firstPresent(
                statusCreatedAt(statusLogs, "event_applications.payment_status", application.get("paymentStatus")),
                application.get("paidAt"),
                application.get("paymentCreatedAt"));
        flow.add(statusStep(
                "PAYMENT",
                "\u4ed8\u6b3e\u6642\u9593",
                paymentReached ? displayPaymentStatus(application) : null,
                paymentReached ? paymentCreatedAt : null));
        flow.add(statusStep(
                "REFUND_REQUESTED",
                "\u9000\u6b3e\u7533\u8acb\u6642\u9593",
                refundRequestedReached ? "\u9000\u6b3e\u7533\u8acb\u4e2d" : null,
                refundRequestedReached
                        ? firstPresent(
                                statusCreatedAt(statusLogs, "refunds.refund_status", "REFUND_REQUESTED"),
                                statusCreatedAt(statusLogs, "event_applications.refund_status", "REFUND_REQUESTED"))
                        : null));
        flow.add(statusStep(
                "REFUND_REVIEW",
                "\u9000\u6b3e\u5be9\u6838\u6642\u9593",
                refundReviewReached ? displayRefundReviewStatus(refundStatus) : null,
                refundReviewReached ? refundReviewCreatedAt(statusLogs, refundStatus) : null));
        flow.add(statusStep(
                "REFUNDED",
                "\u5df2\u9000\u6b3e\u6642\u9593",
                refundedReached ? "\u5df2\u9000\u6b3e" : null,
                refundedReached
                        ? firstPresent(
                                statusCreatedAt(statusLogs, "refunds.refund_status", "REFUNDED"),
                                statusCreatedAt(statusLogs, "event_applications.refund_status", "REFUNDED"),
                                application.get("refundedAt"))
                        : null));
        flow.add(statusStep(
                "STALL_SELECTED",
                "\u9078\u4f4d\u6642\u9593",
                stallSelectedReached ? "\u5df2\u9078\u4f4d" : null,
                stallSelectedReached
                        ? statusCreatedAt(statusLogs, "application_dates.selected_stall_id", application.get("selectedStallId"))
                        : null));
        flow.add(statusStep(
                "DEPOSIT_RETURNED",
                "\u4fdd\u8b49\u91d1\u9000\u9084\u6642\u9593",
                depositReturnedReached ? "\u4fdd\u8b49\u91d1\u5df2\u9000\u9084" : null,
                depositReturnedReached
                        ? firstPresent(
                                statusCreatedAt(statusLogs, "event_applications.deposit_status", "RETURNED"),
                                application.get("refundedAt"))
                        : null));

        return flow;
    }

    private List<Map<String, Object>> applicationStatusLogs(Map<String, Object> application) {
        Long applicationId = toLong(application.get("applicationId"));
        if (applicationId == null) {
            return List.of();
        }

        return organizerRepository.findApplicationStatusLogs(applicationId);
    }

    private Map<String, Object> statusStep(String key, String label, Object value, Object createdAt) {
        return orderedMap(
                "key", key,
                "label", label,
                "value", value,
                "createdAt", formatDateTime(createdAt));
    }

    private Object statusCreatedAt(List<Map<String, Object>> statusLogs, String statusField, Object newStatus) {
        String normalizedField = normalizeText(statusField);
        String normalizedStatus = statusText(newStatus);
        return statusLogs.stream()
                .filter(statusLog -> normalizedField.equals(normalizeText(statusLog.get("statusField"))))
                .filter(statusLog -> normalizedStatus == null
                        || normalizedStatus.equals(statusText(statusLog.get("newStatus"))))
                .map(statusLog -> statusLog.get("createdAt"))
                .findFirst()
                .orElse(null);
    }

    private Object refundReviewCreatedAt(List<Map<String, Object>> statusLogs, String refundStatus) {
        return firstPresent(
                statusCreatedAt(statusLogs, "refunds.refund_status", "REFUNDING"),
                statusCreatedAt(statusLogs, "event_applications.refund_status", "REFUNDING"),
                statusCreatedAt(statusLogs, "refunds.refund_status", "REFUND_FAILED"),
                statusCreatedAt(statusLogs, "event_applications.refund_status", "REFUND_FAILED"),
                statusCreatedAt(statusLogs, "refunds.refund_status", refundStatus),
                statusCreatedAt(statusLogs, "event_applications.refund_status", refundStatus));
    }

    private String displayReviewStatus(Object value) {
        return switch (statusText(value) == null ? "" : statusText(value)) {
            case "APPROVED" -> "\u5be9\u6838\u901a\u904e";
            case "REJECTED" -> "\u5be9\u6838\u672a\u901a\u904e";
            case "PENDING" -> "\u5be9\u6838\u4e2d";
            default -> null;
        };
    }

    private String displayPaymentStatus(Map<String, Object> application) {
        String refundStatus = statusText(application.get("refundStatus"));
        if ("REFUNDED".equals(refundStatus)) {
            return "\u5df2\u9000\u6b3e";
        }
        if ("REFUND_FAILED".equals(refundStatus)) {
            return "\u9000\u6b3e\u5931\u6557";
        }
        if ("REFUNDING".equals(refundStatus)) {
            return "\u9000\u6b3e\u8655\u7406\u4e2d";
        }
        if ("REFUND_REQUESTED".equals(refundStatus)) {
            return "\u9000\u6b3e\u7533\u8acb\u4e2d";
        }

        return switch (statusText(application.get("paymentStatus")) == null ? "" : statusText(application.get("paymentStatus"))) {
            case "PAID" -> "\u4ed8\u6b3e\u6210\u529f";
            case "FAILED" -> "\u4ed8\u6b3e\u5931\u6557";
            case "EXPIRED" -> "\u4ed8\u6b3e\u903e\u671f";
            case "PENDING" -> "\u5f85\u4ed8\u6b3e";
            default -> null;
        };
    }

    private String displayRefundReviewStatus(String refundStatus) {
        return switch (refundStatus == null ? "" : refundStatus) {
            case "REFUNDED" -> "\u9000\u6b3e\u5be9\u6838\u901a\u904e";
            case "REFUNDING" -> "\u9000\u6b3e\u8655\u7406\u4e2d";
            case "REFUND_FAILED" -> "\u9000\u6b3e\u5931\u6557";
            default -> "\u9000\u6b3e\u5be9\u6838\u4e2d";
        };
    }

    private String displayRefundStatus(Object value) {
        return switch (statusText(value) == null ? "" : statusText(value)) {
            case "REFUND_REQUESTED" -> "\u9000\u6b3e\u7533\u8acb\u4e2d";
            case "REFUNDING" -> "\u9000\u6b3e\u8655\u7406\u4e2d";
            case "REFUND_FAILED" -> "\u9000\u6b3e\u5931\u6557";
            case "REFUNDED" -> "\u5df2\u9000\u6b3e";
            default -> null;
        };
    }

    private String displayPublishStatus(Object value) {
        return switch (statusText(value) == null ? "" : statusText(value)) {
            case "DRAFT" -> "\u8349\u7a3f";
            case "READY_TO_PUBLISH" -> "\u5f85\u767c\u5e03";
            case "PUBLISHED" -> "\u5df2\u767c\u5e03";
            case "BRANDS_PUBLISHED" -> "\u6524\u5546\u540d\u55ae\u5df2\u767c\u5e03";
            case "FINAL_REVIEW" -> "\u54c1\u724c\u516c\u958b\u524d\u9a57\u6536";
            case "UNPUBLISH_REQUESTED" -> "\u4e0b\u67b6\u7533\u8acb\u4e2d";
            case "UNPUBLISHED" -> "\u5df2\u4e0b\u67b6";
            case "CANCELLED" -> "\u5df2\u53d6\u6d88";
            default -> normalizeText(value);
        };
    }

    private Object firstPresent(Object... values) {
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private Map<String, Object> getAuthenticatedOrganizer(String authorizationHeader) {
        String token = jwtService.extractTokenFromAuthorizationHeader(authorizationHeader);
        if (token == null || token.isBlank()) {
            return Map.of("message", "Authorization token is required");
        }
        if (!jwtService.isTokenValid(token)) {
            return Map.of("message", "Invalid or expired token");
        }

        String email = jwtService.getEmail(token);
        Map<String, Object> organizer = organizerRepository.findOrganizerAccountByEmail(email)
                .orElse(null);
        if (organizer == null) {
            return Map.of("message", "Organizer profile not found");
        }
        if (!"ORGANIZER".equals(organizer.get("role"))) {
            return Map.of("message", "This account is not an organizer");
        }
        return organizer;
    }

    private boolean isOrganizerProfileIncomplete(Map<String, Object> organizer) {
        return isMissing(organizer.get("organizerName"))
                || isMissing(organizer.get("contactName"))
                || isMissing(organizer.get("contactPhone"))
                || isMissing(organizer.get("contactEmail"))
                || isMissing(organizer.get("city"))
                || isMissing(organizer.get("district"))
                || isMissing(organizer.get("address"))
                || isMissing(organizer.get("serviceDays"))
                || isMissing(organizer.get("serviceStartTime"))
                || isMissing(organizer.get("serviceEndTime"));
    }

    private boolean isMissing(Object value) {
        return value == null || value instanceof String text && text.isBlank();
    }

    private String formatServiceTime(Object time) {
        if (!(time instanceof LocalTime localTime)) {
            return null;
        }
        return localTime.format(SERVICE_TIME_FORMATTER);
    }

    private String validateOrganizerProfile(
            String organizerName,
            String contactName,
            String contactPhone,
            String contactEmail,
            String companyName,
            String taxId,
            String city,
            String district,
            String address,
            String serviceDays,
            String serviceStartTimeText,
            String serviceEndTimeText) {
        if (organizerName == null) {
            return "Organizer name is required";
        }
        if (organizerName.length() > 150) {
            return "Organizer name must not exceed 150 characters";
        }
        if (contactName == null) {
            return "Contact name is required";
        }
        if (contactName.length() > 100) {
            return "Contact name must not exceed 100 characters";
        }
        if (contactPhone == null) {
            return "Contact phone is required";
        }
        if (!TAIWAN_MOBILE_PATTERN.matcher(contactPhone).matches()) {
            return "Contact phone must be 10 digits and start with 09";
        }
        if (contactEmail == null) {
            return "Contact email is required";
        }
        if (contactEmail.length() > 255 || !EMAIL_PATTERN.matcher(contactEmail).matches()) {
            return "Invalid contact email format";
        }
        if (companyName != null && companyName.length() > 150) {
            return "Company name must not exceed 150 characters";
        }
        if (taxId != null && !TAX_ID_PATTERN.matcher(taxId).matches()) {
            return "Tax id must be 8 digits";
        }
        if (city == null) {
            return "City is required";
        }
        if (city.length() > 50) {
            return "City must not exceed 50 characters";
        }
        if (!taiwanAddressService.isValidCity(city)) {
            return "City is invalid";
        }
        if (district == null) {
            return "District is required";
        }
        if (district.length() > 50) {
            return "District must not exceed 50 characters";
        }
        if (!taiwanAddressService.isValidDistrict(city, district)) {
            return "District is invalid for city";
        }
        if (address == null) {
            return "Address is required";
        }
        if (address.length() > 255) {
            return "Address must not exceed 255 characters";
        }
        if (serviceDays == null) {
            return "Service days are required";
        }
        if (!isValidServiceDays(serviceDays)) {
            return "Service days are invalid";
        }
        if (serviceStartTimeText == null) {
            return "Service start time is required";
        }
        LocalTime serviceStartTime = parseServiceTime(serviceStartTimeText);
        if (serviceStartTime == null) {
            return "Service start time format is invalid";
        }
        if (serviceEndTimeText == null) {
            return "Service end time is required";
        }
        LocalTime serviceEndTime = parseServiceTime(serviceEndTimeText);
        if (serviceEndTime == null) {
            return "Service end time format is invalid";
        }
        if (!serviceEndTime.isAfter(serviceStartTime)) {
            return "Service end time must be after start time";
        }
        return null;
    }

    private boolean isValidServiceDays(String value) {
        String[] days = value.split(",");
        if (days.length == 0) {
            return false;
        }

        Set<String> usedDays = new java.util.HashSet<>();
        for (String day : days) {
            String normalizedDay = normalizeText(day);
            if (normalizedDay == null || !SERVICE_DAY_CODES.contains(normalizedDay) || !usedDays.add(normalizedDay)) {
                return false;
            }
        }
        return true;
    }

    private LocalTime parseServiceTime(String value) {
        String text = normalizeText(value);
        if (text == null) {
            return null;
        }
        try {
            return LocalTime.parse(text, SERVICE_TIME_FORMATTER);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private String formatEventDate(Map<String, Object> application) {
        LocalDateTime startAt = toLocalDateTime(application.get("eventStartAt"));
        LocalDateTime endAt = toLocalDateTime(application.get("eventEndAt"));
        if (startAt == null || endAt == null) {
            return null;
        }
        String startDate = startAt.format(DISPLAY_DATE_FORMATTER);
        String endDate = endAt.format(DISPLAY_DATE_FORMATTER);
        return startDate.equals(endDate) ? startDate : startDate + " - " + endDate;
    }

    private String formatEventDateWithWeekday(Map<String, Object> application) {
        LocalDateTime startAt = toLocalDateTime(application.get("eventStartAt"));
        LocalDateTime endAt = toLocalDateTime(application.get("eventEndAt"));
        if (startAt == null || endAt == null) {
            return null;
        }
        String start = startAt.format(DISPLAY_DATE_FORMATTER) + " (" + chineseWeekday(startAt.toLocalDate()) + ")";
        String end = endAt.format(DISPLAY_DATE_FORMATTER) + " (" + chineseWeekday(endAt.toLocalDate()) + ")";
        return startAt.toLocalDate().equals(endAt.toLocalDate()) ? start : start + " - " + end;
    }

    private String formatEventTime(Map<String, Object> application) {
        LocalDateTime startAt = toLocalDateTime(application.get("eventStartAt"));
        LocalDateTime endAt = toLocalDateTime(application.get("eventEndAt"));
        if (startAt == null || endAt == null) {
            return null;
        }
        return startAt.toLocalTime().format(SERVICE_TIME_FORMATTER)
                + " - " + endAt.toLocalTime().format(SERVICE_TIME_FORMATTER);
    }

    private String chineseWeekday(LocalDate date) {
        return switch (date.getDayOfWeek()) {
            case MONDAY -> "一";
            case TUESDAY -> "二";
            case WEDNESDAY -> "三";
            case THURSDAY -> "四";
            case FRIDAY -> "五";
            case SATURDAY -> "六";
            case SUNDAY -> "日";
        };
    }

    private String paymentMethod(Object providerValue) {
        String provider = statusText(providerValue);
        if (provider == null) {
            return null;
        }
        return switch (provider) {
            case "ECPAY" -> "線上付款";
            case "CASH" -> "現金";
            case "TEST" -> "測試付款";
            default -> "線上付款";
        };
    }

    private List<Map<String, Object>> toRefundFeeDetail(
            BigDecimal applicationFee,
            BigDecimal equipmentFee,
            BigDecimal powerFee,
            Object depositAmount,
            Object refundAmount) {
        List<Map<String, Object>> details = new ArrayList<>();
        details.add(orderedMap("item", "報名費", "content", "原報名費退回", "amount", applicationFee));
        details.add(orderedMap("item", "租借費用", "content", "原租借費用退回", "amount", equipmentFee));
        details.add(orderedMap("item", "額外電費", "content", "原額外電費退回", "amount", powerFee));
        details.add(orderedMap("item", "保證金", "content", "保證金不退款（原金額 " + Objects.toString(depositAmount, "0") + "）", "amount", BigDecimal.ZERO));
        details.add(orderedMap("item", "總計", "content", null, "amount", refundAmount));
        return details;
    }

    private List<Map<String, Object>> basicEquipmentResponses(List<Map<String, Object>> equipments) {
        return equipments.stream()
                .filter(row -> "ACTIVE".equals(statusText(row.get("rentalStatus"))))
                .filter(row -> "FREE".equals(statusText(row.get("chargeType"))))
                .filter(row -> "EQUIPMENT".equals(statusText(row.get("itemType"))))
                .map(row -> orderedMap(
                        "equipmentName", row.get("equipmentName"),
                        "specification", row.get("equipmentDescription"),
                        "quantity", row.get("perStallRentalLimit"),
                        "unit", row.get("unit")))
                .toList();
    }

    private List<Map<String, Object>> basicPowerResponses(List<Map<String, Object>> equipments) {
        return equipments.stream()
                .filter(row -> "ACTIVE".equals(statusText(row.get("rentalStatus"))))
                .filter(row -> "FREE".equals(statusText(row.get("chargeType"))))
                .filter(row -> "POWER".equals(statusText(row.get("itemType"))))
                .map(row -> orderedMap(
                        "powerSpecification", powerSpecification(row),
                        "wattage", row.get("wattageLimit")))
                .toList();
    }

    private Map<String, Object> paidEquipmentDetail(Map<String, Object> row, Integer applicationDays) {
        return orderedMap(
                "equipmentName", row.get("equipmentName"),
                "specification", row.get("equipmentDescription"),
                "quantity", row.get("quantity"),
                "unit", quantityUnit(row),
                "unitPrice", row.get("rentalFee"),
                "rentalDays", firstPresent(row.get("rentalUnits"), applicationDays),
                "subtotal", row.get("subtotal"));
    }

    private Map<String, Object> paidPowerDetail(Map<String, Object> row, Integer applicationDays) {
        return orderedMap(
                "powerSpecification", powerSpecification(row),
                "wattage", firstPresent(row.get("totalWattage"), row.get("wattageLimit")),
                "unitPrice", row.get("rentalFee"),
                "rentalDays", firstPresent(row.get("rentalUnits"), applicationDays),
                "subtotal", row.get("subtotal"));
    }

    private String formatEventDateTimeRange(Object startValue, Object endValue) {
        LocalDateTime startAt = toLocalDateTime(startValue);
        LocalDateTime endAt = toLocalDateTime(endValue);
        if (startAt == null && endAt == null) {
            return null;
        }
        if (startAt == null) {
            return DISPLAY_DATE_TIME_FORMATTER.format(endAt);
        }
        if (endAt == null) {
            return DISPLAY_DATE_TIME_FORMATTER.format(startAt);
        }
        return DISPLAY_DATE_TIME_FORMATTER.format(startAt) + " - " + DISPLAY_DATE_TIME_FORMATTER.format(endAt);
    }

    private String formatAppliedAt(Map<String, Object> application) {
        LocalDateTime appliedAt = appliedAtForSort(application);
        return appliedAt == null ? null : appliedAt.format(DISPLAY_DATE_TIME_FORMATTER);
    }

    private String formatDateTime(Object value) {
        LocalDateTime dateTime = toLocalDateTime(value);
        return dateTime == null ? null : dateTime.format(DISPLAY_DATE_TIME_FORMATTER);
    }

    private String formatDate(Object value) {
        LocalDate date = toLocalDate(value);
        return date == null ? null : date.format(DISPLAY_DATE_FORMATTER);
    }

    private String formatTime(Object value) {
        LocalDateTime dateTime = toLocalDateTime(value);
        return dateTime == null ? null : dateTime.toLocalTime().format(SERVICE_TIME_FORMATTER);
    }

    private String normalizeText(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private Integer applicationDays(Object applyDates) {
        String text = normalizeText(applyDates);
        if (text == null) {
            return null;
        }
        int days = 0;
        for (String date : text.split(",")) {
            if (!date.isBlank()) {
                days++;
            }
        }
        return days == 0 ? null : days;
    }

    private boolean isAllApplicationDatesSelected(Map<String, Object> application) {
        Long applicationDateCount = toLong(application.get("applicationDateCount"));
        Long selectedStallCount = toLong(application.get("selectedStallCount"));
        if (applicationDateCount != null && applicationDateCount > 0) {
            return applicationDateCount.equals(selectedStallCount);
        }
        return application.get("selectedStallId") != null;
    }

    private String dayLabel(Integer days) {
        return days == null ? null : days + "\u5929";
    }

    private String moneyText(Object value) {
        BigDecimal amount = toBigDecimal(value);
        if (amount == null) {
            return null;
        }
        return "NT$" + amount.stripTrailingZeros().toPlainString();
    }

    private String decimalText(Object value) {
        BigDecimal decimal = toBigDecimal(value);
        if (decimal == null) {
            return null;
        }
        return decimal.stripTrailingZeros().toPlainString();
    }

    private String integerText(Object value) {
        if (value instanceof Number number) {
            return String.valueOf(number.intValue());
        }
        String text = normalizeText(value);
        if (text == null) {
            return null;
        }
        try {
            return String.valueOf(new BigDecimal(text).intValue());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String pricingUnitText(Object value) {
        return switch (statusText(value) == null ? "" : statusText(value)) {
            case "DAY" -> "\u5929";
            case "HOUR" -> "\u5c0f\u6642";
            default -> null;
        };
    }

    private String quantityUnit(Map<String, Object> row) {
        String pricingUnit = pricingUnitText(row.get("pricingUnit"));
        return pricingUnit == null ? "個數" : pricingUnit;
    }

    private String unitContent(String prefix, Integer count, String unit) {
        if (count == null) {
            return null;
        }
        return prefix + count + unit;
    }

    private boolean isActiveEquipment(Map<String, Object> row) {
        return "EQUIPMENT".equals(statusText(row.get("itemType")))
                && "ACTIVE".equals(statusText(row.get("rentalStatus")));
    }

    private String voltageType(Map<String, Object> row) {
        if ("POWER".equals(statusText(row.get("itemType")))) {
            return voltageWithUnit(row.get("equipmentName"));
        }
        String combined = normalizeText(row.get("equipmentName"));
        String description = normalizeText(row.get("equipmentDescription"));
        if (description != null) {
            combined = combined == null ? description : combined + " " + description;
        }
        if (combined == null) {
            return null;
        }
        Matcher matcher = VOLTAGE_PATTERN.matcher(combined);
        if (!matcher.find()) {
            return null;
        }
        return voltageWithUnit(matcher.group(1));
    }

    private String powerPlan(Map<String, Object> row) {
        String voltage = voltageType(row);
        String wattage = wattageWithUnit(row.get("wattageLimit"));
        if (voltage != null && wattage != null) {
            return voltage + "/" + wattage;
        }
        if (wattage != null) {
            String name = normalizeText(row.get("equipmentName"));
            return name == null ? wattage : voltageWithUnit(name) + "/" + wattage;
        }
        return normalizeText(row.get("equipmentName"));
    }

    private String voltageWithUnit(Object value) {
        String voltage = normalizeText(value);
        if (voltage == null) {
            return null;
        }
        return voltage.toLowerCase().endsWith("v") ? voltage : voltage + "v";
    }

    private String wattageWithUnit(Object value) {
        Integer wattage = nullableInt(value);
        return wattage == null ? null : wattage + "w";
    }

    private int intValue(Object value) {
        Integer number = nullableInt(value);
        return number == null ? 0 : number;
    }

    private Integer sumNullableInts(Object firstValue, Object secondValue) {
        Integer first = nullableInt(firstValue);
        Integer second = nullableInt(secondValue);
        if (first == null && second == null) {
            return null;
        }
        return intValue(first) + intValue(second);
    }

    private byte[] buildEquipmentReportWorkbook(Map<String, Object> response) {
        Map<String, Object> equipmentRentalManagement = mapValue(response.get("equipmentRentalManagement"));
        List<Map<String, Object>> equipmentRentalItems = listValue(equipmentRentalManagement.get("items"));
        List<String> equipmentColumns = dynamicQuantityColumns(equipmentRentalItems);

        return workbookBytes(List.of(
                sheet("活動資訊",
                        List.of("活動ID", "活動名稱", "狀態", "狀態說明", "活動時間", "地點", "地址"),
                        List.of(row(response.get("event"),
                                "eventId", "eventTitle", "status", "statusNote", "eventTime", "locationName", "address"))),
                sheet("活動設備",
                        List.of("設備名稱", "設備類型", "單位", "免費提供數量", "付費租借上限", "每日可租借總數", "付費租金", "計費單位", "租借狀態", "說明"),
                        rows(response.get("eventEquipments"),
                                "equipmentName", "itemType", "unit", "freeProvided", "perStallRentalLimit",
                                "dailyRentableQuantity", "paidRentalFee", "pricingUnit", "rentalStatus", "description")),
                sheet("基本用電",
                        List.of("用電名稱", "電壓", "免費瓦數"),
                        rows(response.get("basicPowers"), "equipmentName", "voltageType", "freeWattage")),
                sheet("加購用電",
                        List.of("用電名稱", "用電方案", "每攤可提供組數", "可提供組數"),
                        rows(response.get("extraPowers"), "equipmentName", "powerPlan", "perStallProvidedQuantity", "availableGroupQuantity")),
                sheet("設備租借統計",
                        List.of("設備名稱", "已租借數量", "可租借數量", "剩餘數量", "租借/總計"),
                        rows(response.get("equipmentRentalStatistics"),
                                "equipmentName", "rentedQuantity", "rentableQuantity", "remainingQuantity", "rentedText")),
                sheet("設備租借管理",
                        equipmentManagementHeaders(equipmentColumns),
                        equipmentManagementRows(equipmentRentalItems, equipmentColumns)),
                sheet("加購用電管理",
                        List.of("攤位編號", "品牌名稱", "用電方案"),
                        rows(mapValue(response.get("extraPowerManagement")).get("items"), "stallNo", "brandName", "powerPlan")),
                sheet("車輛管理",
                        List.of("攤位編號", "品牌名稱", "聯絡人", "車牌號碼"),
                        rows(mapValue(response.get("vehicleManagement")).get("items"), "stallNo", "brandName", "contactName", "vehicleNo"))));
    }

    private byte[] buildAccountReportWorkbook(Map<String, Object> response) {
        Map<String, Object> summary = mapValue(response.get("summary"));
        Map<String, Object> statistics = mapValue(response.get("statistics"));
        Map<String, Object> payment = mapValue(statistics.get("payment"));
        Map<String, Object> refund = mapValue(statistics.get("refund"));
        Map<String, Object> deposit = mapValue(statistics.get("deposit"));

        List<List<Object>> summaryRows = List.of(
                reportRow("收款總額", value(summary, "grossRevenue")),
                reportRow("退款總額", value(summary, "refundAmount")),
                reportRow("已退保證金總額", value(summary, "returnedDepositAmount")),
                reportRow("未退保證金總額", value(summary, "unreturnedDepositAmount")),
                reportRow("實收總額", value(summary, "netRevenue")),
                reportRow("總攤位數", value(payment, "totalStallCount")),
                reportRow("已付款攤位數", value(payment, "paidStallCount")),
                reportRow("待付款攤位數", value(payment, "pendingPaymentStallCount")),
                reportRow("退款筆數", value(refund, "refundCount")),
                reportRow("已退款筆數", value(refund, "refundedCount")),
                reportRow("退款中筆數", value(refund, "refundingCount")),
                reportRow("已退保證金筆數", value(deposit, "returnedDepositCount")),
                reportRow("未退保證金筆數", value(deposit, "unreturnedDepositCount")));

        return workbookBytes(List.of(
                sheet("活動資訊",
                        List.of("活動ID", "活動名稱", "發布狀態", "狀態文字", "狀態說明", "活動日期", "地點", "地址", "總攤位數", "已付款攤位數"),
                        List.of(row(response.get("event"),
                                "eventId", "eventTitle", "publishStatus", "publishStatusText", "statusNote",
                                "eventDate", "locationName", "address", "totalStallCount", "paidStallCount"))),
                new ReportSheet("帳務摘要", List.of("項目", "值"), summaryRows),
                sheet("付款明細",
                        List.of("付款編號", "品牌名稱", "攤主名稱", "付款時間", "付款金額", "退款金額", "保證金狀態", "帳務狀態"),
                        rows(response.get("payments"),
                                "paymentNo", "brandName", "contactName", "paidAt", "paymentAmount", "refundAmount", "depositStatus", "accountingStatus"))));
    }

    private ReportSheet sheet(String name, List<String> headers, List<List<Object>> rows) {
        return new ReportSheet(name, headers, rows);
    }

    private List<Object> reportRow(Object... values) {
        List<Object> row = new ArrayList<>();
        for (Object value : values) {
            row.add(value);
        }
        return row;
    }

    private byte[] workbookBytes(List<ReportSheet> reportSheets) {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            for (ReportSheet reportSheet : reportSheets) {
                Sheet sheet = workbook.createSheet(safeSheetName(reportSheet.name()));
                writeSheet(sheet, reportSheet, headerStyle);
            }
            workbook.write(bytes);
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private void writeSheet(Sheet sheet, ReportSheet reportSheet, CellStyle headerStyle) {
        Row headerRow = sheet.createRow(0);
        for (int columnIndex = 0; columnIndex < reportSheet.headers().size(); columnIndex++) {
            Cell cell = headerRow.createCell(columnIndex);
            cell.setCellValue(reportSheet.headers().get(columnIndex));
            cell.setCellStyle(headerStyle);
        }

        for (int rowIndex = 0; rowIndex < reportSheet.rows().size(); rowIndex++) {
            Row excelRow = sheet.createRow(rowIndex + 1);
            List<Object> rowValues = reportSheet.rows().get(rowIndex);
            for (int columnIndex = 0; columnIndex < rowValues.size(); columnIndex++) {
                writeCell(excelRow.createCell(columnIndex), rowValues.get(columnIndex));
            }
        }

        sheet.createFreezePane(0, 1);
        for (int columnIndex = 0; columnIndex < reportSheet.headers().size(); columnIndex++) {
            sheet.autoSizeColumn(columnIndex);
            int width = sheet.getColumnWidth(columnIndex);
            sheet.setColumnWidth(columnIndex, Math.min(Math.max(width + 512, 2800), 12000));
        }
    }

    private void writeCell(Cell cell, Object value) {
        if (value == null) {
            cell.setBlank();
            return;
        }
        if (value instanceof BigDecimal decimal) {
            cell.setCellValue(decimal.doubleValue());
            return;
        }
        if (value instanceof Number number) {
            cell.setCellValue(number.doubleValue());
            return;
        }
        if (value instanceof Boolean bool) {
            cell.setCellValue(bool);
            return;
        }
        cell.setCellValue(String.valueOf(value));
    }

    private String safeSheetName(String name) {
        String safeName = normalizeText(name);
        if (safeName == null) {
            return "Sheet";
        }
        safeName = safeName.replaceAll("[\\\\/?*\\[\\]:]", "_");
        return safeName.length() > 31 ? safeName.substring(0, 31) : safeName;
    }

    private List<List<Object>> rows(Object value, String... keys) {
        return listValue(value).stream()
                .map(row -> row(row, keys))
                .toList();
    }

    private List<Object> row(Object value, String... keys) {
        Map<String, Object> map = mapValue(value);
        List<Object> row = new ArrayList<>();
        for (String key : keys) {
            row.add(map.get(key));
        }
        return row;
    }

    private Object value(Map<String, Object> map, String key) {
        return map == null ? null : map.get(key);
    }

    private List<String> dynamicQuantityColumns(List<Map<String, Object>> items) {
        Set<String> columns = new LinkedHashSet<>();
        for (Map<String, Object> item : items) {
            mapValue(item.get("equipmentQuantities")).keySet().stream()
                    .map(this::normalizeText)
                    .filter(Objects::nonNull)
                    .forEach(columns::add);
        }
        return List.copyOf(columns);
    }

    private List<String> equipmentManagementHeaders(List<String> equipmentColumns) {
        List<String> headers = new ArrayList<>(List.of("攤位編號", "品牌名稱"));
        headers.addAll(equipmentColumns);
        return headers;
    }

    private List<List<Object>> equipmentManagementRows(
            List<Map<String, Object>> items,
            List<String> equipmentColumns) {
        List<List<Object>> rows = new ArrayList<>();
        for (Map<String, Object> item : items) {
            Map<String, Object> quantities = mapValue(item.get("equipmentQuantities"));
            List<Object> row = new ArrayList<>();
            row.add(item.get("stallNo"));
            row.add(item.get("brandName"));
            equipmentColumns.forEach(column -> row.add(quantities.get(column)));
            rows.add(row);
        }
        return rows;
    }

    private String reportFilename(String prefix, Object eventValue, Long eventId) {
        String eventTitle = normalizeText(mapValue(eventValue).get("eventTitle"));
        String suffix = eventTitle == null ? String.valueOf(eventId) : eventTitle;
        return prefix + "-" + safeFilename(suffix) + ".xlsx";
    }

    private String safeFilename(String text) {
        return text.replaceAll("[\\\\/:*?\"<>|]+", "_").replaceAll("\\s+", "-");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listValue(Object value) {
        if (value instanceof List<?> list) {
            return (List<Map<String, Object>>) list;
        }
        return List.of();
    }

    private Map<String, Object> message(String message) {
        return Map.of("message", message);
    }

    private record ReportSheet(String name, List<String> headers, List<List<Object>> rows) {
    }

    private Integer nullableInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        String text = normalizeText(value);
        if (text == null) {
            return null;
        }
        try {
            return new BigDecimal(text).intValue();
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String statusText(Object value) {
        String text = normalizeText(value);
        return text == null ? null : text.toUpperCase();
    }

    private boolean isTrue(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof Number numberValue) {
            return numberValue.intValue() == 1;
        }
        String text = statusText(value);
        return "TRUE".equals(text) || "1".equals(text);
    }

    private Boolean nullableBoolean(Object value) {
        return value == null ? null : isTrue(value);
    }

    private String joinAddress(Object city, Object district, Object address) {
        StringBuilder builder = new StringBuilder();
        appendIfPresent(builder, city);
        appendIfPresent(builder, district);
        appendIfPresent(builder, address);
        return builder.isEmpty() ? null : builder.toString();
    }

    private void appendIfPresent(StringBuilder builder, Object value) {
        String text = normalizeText(value);
        if (text != null) {
            builder.append(text);
        }
    }

    private Map<String, Object> orderedMap(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            map.put(keyValues[i].toString(), keyValues[i + 1]);
        }
        return map;
    }

    private BigDecimal subtractAmounts(Object totalAmount, Object baseFee, Object depositAmount) {
        BigDecimal total = toBigDecimal(totalAmount);
        if (total == null) {
            return null;
        }
        BigDecimal base = toBigDecimal(baseFee);
        BigDecimal deposit = toBigDecimal(depositAmount);
        return total
                .subtract(base == null ? BigDecimal.ZERO : base)
                .subtract(deposit == null ? BigDecimal.ZERO : deposit);
    }

    private BigDecimal multiply(Object amount, Integer multiplier) {
        BigDecimal value = toBigDecimal(amount);
        if (value == null || multiplier == null) {
            return value;
        }
        return value.multiply(BigDecimal.valueOf(multiplier));
    }

    private BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        String text = normalizeText(value);
        if (text == null) {
            return null;
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private LocalDateTime appliedAtForSort(Map<String, Object> application) {
        Object value = application.get("appliedAt");
        return toLocalDateTime(value);
    }

    private LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime.toLocalDate();
        }
        if (value instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toLocalDateTime().toLocalDate();
        }
        String text = normalizeText(value);
        if (text == null) {
            return null;
        }
        try {
            return LocalDate.parse(text.length() > 10 ? text.substring(0, 10) : text);
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        String text = normalizeText(value);
        if (text == null) {
            return null;
        }
        try {
            if (text.contains(" ")) {
                return LocalDateTime.parse(text, SPACE_DATE_TIME_FORMATTER);
            }
            return LocalDateTime.parse(text);
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        String text = normalizeText(value);
        if (text == null) {
            return null;
        }
        try {
            return Long.valueOf(text);
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
