package com.example.demo.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.demo.Repository.OrganizerRepository;
import com.example.demo.dto.request.OrganizerApplicationReviewRequest;
import com.example.demo.dto.response.ApiResponse;
import com.example.demo.dto.response.MapBackedResponse;
import com.example.demo.dto.response.OrganizerAccountResponse;
import com.example.demo.dto.response.OrganizerApplicationDetailResponse;
import com.example.demo.dto.response.OrganizerApplicationSearchResponse;
import com.example.demo.dto.response.OrganizerApplicationSummaryResponse;
import com.example.demo.dto.response.OrganizerAccountingSearchResponse;
import com.example.demo.dto.response.OrganizerAccountingSummaryResponse;
import com.example.demo.dto.response.OrganizerStallEventSearchResponse;
import com.example.demo.dto.response.OrganizerStallEventSummaryResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class OrganizerService {

    private static final DateTimeFormatter SERVICE_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter SPACE_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DISPLAY_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DISPLAY_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final TypeReference<Map<String, Object>> STRING_OBJECT_MAP = new TypeReference<>() {
    };
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private OrganizerRepository organizerRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ApplicationStatusService applicationStatusService;

    public ApiResponse<OrganizerAccountResponse> getOrganizerAccount(String authorizationHeader) {
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
        account.put("address", joinAddress(
                organizer.get("city"),
                organizer.get("district"),
                organizer.get("address")));
        account.put("serviceDays", organizer.get("serviceDays"));
        account.put("serviceTime", formatServiceTime(
                organizer.get("serviceStartTime"),
                organizer.get("serviceEndTime")));
        return ApiResponse.success("Organizer account retrieved successfully", new OrganizerAccountResponse(account));
    }

    public ApiResponse<OrganizerAccountingSearchResponse> searchOrganizerAccounts(
            String authorizationHeader,
            String eventTitle,
            String status,
            LocalDate eventStartAt,
            LocalDate eventEndAt) {
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
                new OrganizerAccountingSearchResponse(accounts));
    }

    public ApiResponse<MapBackedResponse> getOrganizerAccountDetail(
            String authorizationHeader,
            Long eventId,
            String status) {
        Map<String, Object> organizer = getAuthenticatedOrganizer(authorizationHeader);
        if (organizer.containsKey("message")) {
            return ApiResponse.fail(organizer.get("message").toString());
        }
        if (eventId == null) {
            return ApiResponse.fail("Event id is required");
        }

        Long organizerUserId = ((Number) organizer.get("userId")).longValue();
        Map<String, Object> account = organizerRepository
                .findOrganizerAccountingEventDetail(organizerUserId, eventId)
                .orElse(null);
        if (account == null) {
            return ApiResponse.fail("Event not found");
        }

        List<Map<String, Object>> payments = organizerRepository
                .findOrganizerAccountingPaymentDetails(eventId)
                .stream()
                .map(this::toAccountingPaymentDetailResponse)
                .filter(payment -> matchesAccountingStatus(payment, status))
                .toList();

        return ApiResponse.success(
                "Organizer accounting detail retrieved successfully",
                new MapBackedResponse(orderedMap(
                        "event", toAccountingEventResponse(withDisplayPublishStatus(account)),
                        "summary", toAccountingFinancialSummary(account),
                        "statistics", toAccountingStatistics(account),
                        "payments", payments)));
    }

    public ApiResponse<OrganizerApplicationSearchResponse> searchOrganizerApplications(
            String authorizationHeader,
            String eventTitle,
            String status,
            String brandName,
            LocalDate registrationStartAt,
            LocalDate registrationEndAt) {
        Map<String, Object> organizer = getAuthenticatedOrganizer(authorizationHeader);
        if (organizer.containsKey("message")) {
            return ApiResponse.fail(organizer.get("message").toString());
        }

        Long organizerUserId = ((Number) organizer.get("userId")).longValue();
        LocalDateTime appliedStartAt = registrationStartAt == null ? null : registrationStartAt.atStartOfDay();
        LocalDateTime appliedEndExclusive = registrationEndAt == null ? null : registrationEndAt.plusDays(1).atStartOfDay();
        List<OrganizerApplicationSummaryResponse> applications = organizerRepository
                .findOrganizerApplications(organizerUserId, eventTitle, brandName, appliedStartAt, appliedEndExclusive)
                .stream()
                .map(this::withDisplayApplicationStatus)
                .filter(application -> matchesApplicationStatus(application, status))
                .map(this::toApplicationSummaryResponse)
                .map(OrganizerApplicationSummaryResponse::new)
                .toList();
        return ApiResponse.success(
                "Organizer applications retrieved successfully",
                new OrganizerApplicationSearchResponse(applications));
    }

    public ApiResponse<OrganizerStallEventSearchResponse> searchOrganizerStallEvents(
            String authorizationHeader,
            String eventTitle,
            String status,
            LocalDate eventStartAt,
            LocalDate eventEndAt) {
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
                new OrganizerStallEventSearchResponse(events));
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

        List<Map<String, Object>> equipmentRentals = organizerRepository.findApplicationEquipmentRentals(applicationId);
        List<Map<String, Object>> applicationDates = organizerRepository.findApplicationDates(applicationId);
        Map<String, Object> response = toApplicationDetailResponse(
                withDisplayApplicationStatus(application),
                applicationDates,
                equipmentRentals);
        response.put("status", toApplicationStatusFlow(application));
        return ApiResponse.success(
                "Organizer application detail retrieved successfully",
                new OrganizerApplicationDetailResponse(response));
    }

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
        String reviewNotePayload = null;
        if ("REJECTED".equals(reviewStatus)) {
            reviewNote = normalizeText(rawReviewNote);
            reviewNoteDetail = normalizeText(rawReviewNoteDetail);
            reviewNotePayload = reviewNotePayload(reviewNote, reviewNoteDetail);
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
                reviewStatus,
                reviewNotePayload);
        if (updatedRows == 0) {
            return ApiResponse.fail("Application review failed");
        }

        return ApiResponse.success(
                "Organizer application reviewed successfully",
                new MapBackedResponse(orderedMap(
                        "applicationId", applicationId,
                        "applicationNo", application.get("applicationNo"),
                        "reviewStatus", reviewStatus,
                        "reviewNote", reviewNote,
                        "reviewNoteDetail", reviewNoteDetail)));
    }

    private Map<String, Object> withDisplayApplicationStatus(Map<String, Object> application) {
        Map<String, Object> response = new LinkedHashMap<>(application);
        response.put("applicationStatus", applicationStatusService.resolveApplicationStatus(application));
        return response;
    }

    private Map<String, Object> withDisplayPublishStatus(Map<String, Object> account) {
        Map<String, Object> response = new LinkedHashMap<>(account);
        response.put("publishStatusText", displayPublishStatus(account.get("publishStatus")));
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

    private Map<String, Object> toAccountingSummaryResponse(Map<String, Object> account) {
        Object paidStallCount = account.get("paidStallCount");
        Object totalStallCount = account.get("totalStallCount");
        return orderedMap(
                "eventId", account.get("eventId"),
                "eventTitle", account.get("eventTitle"),
                "publishStatus", account.get("publishStatus"),
                "publishStatusText", account.get("publishStatusText"),
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
                "paymentNo", payment.get("paymentNo"),
                "brandName", payment.get("brandName"),
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
                "status", event.get("status"));
    }

    private Map<String, Object> toApplicationSummaryResponse(Map<String, Object> application) {
        return orderedMap(
                "applicationId", application.get("applicationId"),
                "eventTitle", application.get("eventTitle"),
                "eventTime", application.get("eventTime"),
                "vendorName", application.get("vendorName"),
                "brandType", application.get("brandType"),
                "vendorOwnerName", application.get("vendorOwnerName"),
                "appliedAt", formatAppliedAt(application),
                "applicationStatus", application.get("applicationStatus"));
    }

    private Map<String, Object> toApplicationDetailResponse(
            Map<String, Object> application,
            List<Map<String, Object>> applicationDateRows,
            List<Map<String, Object>> equipmentRentalRows) {
        Map<String, Object> response = new LinkedHashMap<>();
        Map<String, Object> reviewNote = parseReviewNote(application.get("reviewNote"));
        response.put("application", orderedMap(
                "applicationId", application.get("applicationId"),
                "applicationNo", application.get("applicationNo"),
                "applicationStatus", application.get("applicationStatus")));

        response.put("event", orderedMap(
                "eventTitle", application.get("eventTitle"),
                "eventStatus", displayEventStatus(application),
                "eventTime", formatEventDate(application),
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
                "brandName", application.get("vendorName"),
                "categoryName", application.get("categoryName"),
                "brandDescription", application.get("brandDescription")));

        response.put("applicationdetail", orderedMap(
                "registrationPeriods", toRegistrationPeriods(application, applicationDateRows),
                "stallSize", stallSize(application),
                "stallZone", application.get("stallZoneName"),
                "stallCategory", application.get("categoryName"),
                "vehicleNo", application.get("vehicleNo"),
                "applicantNote", application.get("applicantNote"),
                "reviewNote", reviewNote.get("reviewNote"),
                "reviewNoteDetail", reviewNote.get("reviewNoteDetail")));

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
                "paymentAmount", firstPresent(application.get("paymentAmount"), totalAmount)));
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

    private String reviewNotePayload(String reviewNote, String reviewNoteDetail) {
        if (reviewNote == null && reviewNoteDetail == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(orderedMap(
                    "reviewNote", reviewNote,
                    "reviewNoteDetail", reviewNoteDetail));
        } catch (JsonProcessingException e) {
            return reviewNote;
        }
    }

    private Map<String, Object> parseReviewNote(Object value) {
        String text = normalizeText(value);
        if (text == null) {
            return orderedMap(
                    "reviewNote", null,
                    "reviewNoteDetail", null);
        }
        if (text.startsWith("{")) {
            try {
                Map<String, Object> parsed = objectMapper.readValue(text, STRING_OBJECT_MAP);
                return orderedMap(
                        "reviewNote", normalizeText(parsed.get("reviewNote")),
                        "reviewNoteDetail", normalizeText(parsed.get("reviewNoteDetail")));
            } catch (JsonProcessingException ignored) {
                // Fall through to legacy plain-text handling.
            }
        }
        return orderedMap(
                "reviewNote", text,
                "reviewNoteDetail", null);
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

    private String stallSize(Map<String, Object> application) {
        String length = decimalText(application.get("stallLength"));
        String height = decimalText(application.get("stallHeight"));
        if (length == null || height == null) {
            return null;
        }
        return length + "x" + height;
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
                && !cancelled;
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

    private String formatServiceTime(Object startTime, Object endTime) {
        if (!(startTime instanceof LocalTime start) || !(endTime instanceof LocalTime end)) {
            return null;
        }
        return start.format(SERVICE_TIME_FORMATTER) + "-" + end.format(SERVICE_TIME_FORMATTER);
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
        if (applicationDateCount > 0) {
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
