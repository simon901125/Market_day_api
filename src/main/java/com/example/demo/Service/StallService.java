package com.example.demo.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import com.example.demo.Repository.OrganizerRepository;
import com.example.demo.Repository.StallRepository;
import com.example.demo.dto.request.StallSelectionRequest;
import com.example.demo.dto.request.VendorProductSaveRequest;
import com.example.demo.dto.request.VendorStallSaveRequest;
import com.example.demo.dto.response.ApiResponse;
import com.example.demo.dto.response.EventStallStatusResponse;
import com.example.demo.dto.response.MapBackedResponse;
import com.example.demo.dto.response.StallSelectionResponse;
import com.example.demo.dto.response.VendorAccountResponse;
import com.example.demo.dto.response.VendorStallMapResponse;

@Service
public class StallService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern TAIWAN_MOBILE_PATTERN = Pattern.compile("^09\\d{8}$");

    @Autowired
    private StallRepository stallRepository;

    @Autowired
    private OrganizerRepository organizerRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ApplicationStatusService applicationStatusService;

    @Autowired
    private TaiwanAddressService taiwanAddressService;

    @Transactional
    public ApiResponse<StallSelectionResponse> selectEventStall(
            String authorizationHeader,
            StallSelectionRequest body) {
        String token = jwtService.extractTokenFromAuthorizationHeader(authorizationHeader);
        if (token == null || token.isBlank()) {
            return ApiResponse.fail("Authorization token is required");
        }
        if (!jwtService.isTokenValid(token)) {
            return ApiResponse.fail("Invalid or expired token");
        }
        if (!"VENDOR".equals(jwtService.getRole(token))) {
            return ApiResponse.fail("This account is not a vendor");
        }

        if (body.getApplicationNo() == null || body.getApplicationNo().isBlank()) {
            return ApiResponse.fail("Application number is required");
        }

        if (body.getSelections() == null || body.getSelections().isEmpty()) {
            return ApiResponse.fail("Stall selections are required");
        }

        String email = jwtService.getEmail(token);
        Map<String, Object> vendor = stallRepository.findVendorAccountByEmail(email)
                .orElse(null);
        if (vendor == null) {
            return ApiResponse.fail("Vendor profile not found");
        }
        if (!"VENDOR".equals(vendor.get("role"))) {
            return ApiResponse.fail("This account is not a vendor");
        }

        Map<String, Object> application = stallRepository.findApplicationForSelection(body.getApplicationNo())
                .orElse(null);
        if (application == null) {
            return ApiResponse.fail("Application not found");
        }

        Long vendorUserId = ((Number) vendor.get("userId")).longValue();
        Long applicationUserId = ((Number) application.get("userId")).longValue();
        if (!vendorUserId.equals(applicationUserId)) {
            return ApiResponse.fail("Application does not belong to this account");
        }

        if (isTrue(application.get("isCancelled"))) {
            return ApiResponse.fail("Application has been cancelled");
        }

        String reviewStatus = stringValue(application.get("reviewStatus"));
        if ("PENDING".equals(reviewStatus)) {
            return ApiResponse.fail("Application review is pending");
        }
        if ("REJECTED".equals(reviewStatus)) {
            return ApiResponse.fail("Application review was rejected");
        }
        if (!"APPROVED".equals(reviewStatus)) {
            return ApiResponse.fail("Application is not approved");
        }

        String paymentStatus = stringValue(application.get("paymentStatus"));
        if ("PENDING".equals(paymentStatus)) {
            return ApiResponse.fail("Application payment is pending");
        }
        if (!"PAID".equals(paymentStatus)) {
            return ApiResponse.fail("Application payment is not paid");
        }

        Long applicationId = ((Number) application.get("applicationId")).longValue();
        List<Map<String, Object>> applicationDates = stallRepository.findApplicationDatesForSelection(applicationId);
        if (applicationDates.isEmpty()) {
            return ApiResponse.fail("Application dates are required");
        }

        Long eventId = ((Number) application.get("eventId")).longValue();
        Map<LocalDate, Map<String, Object>> applicationDatesByDate = new HashMap<>();
        for (Map<String, Object> applicationDate : applicationDates) {
            LocalDate applyDate = toLocalDate(applicationDate.get("applyDate"));
            if (applyDate != null) {
                applicationDatesByDate.put(applyDate, applicationDate);
            }
        }
        Map<LocalDate, String> requestedSelections = new HashMap<>();
        for (StallSelectionRequest.Selection selection : body.getSelections()) {
            if (selection == null || selection.getApplyDate() == null) {
                return ApiResponse.fail("Apply date is required");
            }
            if (selection.getStallNo() == null || selection.getStallNo().isBlank()) {
                return ApiResponse.fail("Stall number is required");
            }
            if (requestedSelections.put(selection.getApplyDate(), selection.getStallNo().trim()) != null) {
                return ApiResponse.fail("Duplicate apply date in stall selections");
            }
        }
        for (LocalDate requestedDate : requestedSelections.keySet()) {
            Map<String, Object> applicationDate = applicationDatesByDate.get(requestedDate);
            if (applicationDate == null) {
                return ApiResponse.fail("Apply date is not part of this application");
            }
            if (applicationDate.get("selectedStallId") != null) {
                return ApiResponse.fail("Application date has already selected a stall");
            }
        }

        for (Map.Entry<LocalDate, String> requestedSelection : requestedSelections.entrySet()) {
            Map<String, Object> stall = stallRepository.findStallForSelection(eventId, requestedSelection.getValue())
                    .orElse(null);
            if (stall == null) {
                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                return ApiResponse.fail("Invalid stall selection");
            }

            String stallStatus = stringValue(stall.get("status"));
            if (!"AVAILABLE".equals(stallStatus)) {
                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                return ApiResponse.fail("Stall is not available");
            }

            Long stallId = ((Number) stall.get("id")).longValue();
            int updatedRows;
            try {
                updatedRows = stallRepository.bindApplicationDateSelectedStall(
                        applicationId,
                        requestedSelection.getKey(),
                        stallId);
            } catch (DataIntegrityViolationException exception) {
                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                return ApiResponse.fail("Stall has already been selected");
            }
            if (updatedRows == 0) {
                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                return ApiResponse.fail("Stall has already been selected");
            }
        }

        List<StallSelectionResponse.Selection> selectedResults = stallRepository
                .findSelectedApplicationDates(body.getApplicationNo())
                .stream()
                .map(selectedDate -> new StallSelectionResponse.Selection(
                        toLocalDate(selectedDate.get("applyDate")),
                        normalizeText(selectedDate.get("stallNo"))))
                .toList();

        return ApiResponse.success(
                "Stall selection successful",
                new StallSelectionResponse(body.getApplicationNo(), selectedResults));
    }

    public ApiResponse<List<EventStallStatusResponse>> getPublicEventStallsStatus(Long eventId, LocalDate applyDate) {
        if (eventId == null) {
            return ApiResponse.fail("Event id is required");
        }
        Map<String, Object> eventData = stallRepository.findEventForStallStatus(eventId)
                .orElse(null);
        if (eventData == null) {
            return ApiResponse.fail("Event not found");
        }

        LocalDate startDate = toLocalDate(eventData.get("startAt"));
        LocalDate endDate = toLocalDate(eventData.get("endAt"));
        if (startDate == null || endDate == null) {
            return ApiResponse.fail("Apply date is required");
        }

        if (applyDate != null && !isDateInEventRange(applyDate, eventData)) {
            return ApiResponse.fail("Apply date is not part of this event");
        }

        LocalDate targetDate = applyDate == null ? startDate : applyDate;
        List<EventStallStatusResponse> stalls = stallRepository.findEventStallsStatus(eventId, targetDate).stream()
                .map(stall -> withCurrentApplyDate(stall, targetDate))
                .map(this::withDisplayBoothStatus)
                .map(EventStallStatusResponse::new)
                .toList();

        return ApiResponse.success("Event stalls status retrieved successfully", stalls);
    }

    public ApiResponse<VendorAccountResponse> getVendorAccount(String authorizationHeader) {
        String token = jwtService.extractTokenFromAuthorizationHeader(authorizationHeader);
        if (token == null || token.isBlank()) {
            return ApiResponse.fail("Authorization token is required");
        }
        if (!jwtService.isTokenValid(token)) {
            return ApiResponse.fail("Invalid or expired token");
        }

        String email = jwtService.getEmail(token);
        Map<String, Object> vendor = stallRepository.findVendorAccountByEmail(email)
                .orElse(null);
        if (vendor == null) {
            return ApiResponse.fail("Vendor profile not found");
        }
        if (!"VENDOR".equals(vendor.get("role"))) {
            return ApiResponse.fail("This account is not a vendor");
        }

        String provider = vendor.get("provider") == null ? "" : vendor.get("provider").toString().toLowerCase();
        Map<String, Object> account = new LinkedHashMap<>();
        account.put("userId", vendor.get("userId"));
        account.put("userProfileId", vendor.get("userProfileId"));
        account.put("vendorProfileId", vendor.get("vendorProfileId"));
        account.put("name", vendor.get("name"));
        account.put("email", vendor.get("email"));
        account.put("loginProvider", provider);
        account.put("contactName", vendor.get("contactName"));
        account.put("contactPhone", vendor.get("contactPhone"));
        account.put("contactEmail", vendor.get("contactEmail"));
        account.put("city", vendor.get("city"));
        account.put("district", vendor.get("district"));
        account.put("address", joinAddress(
                vendor.get("city"),
                vendor.get("district"),
                vendor.get("address")));
        account.put("categoryId", vendor.get("categoryId"));
        account.put("categoryName", vendor.get("categoryName"));
        account.put("categorySlug", vendor.get("categorySlug"));
        account.put("instagramUrl", vendor.get("instagramUrl"));
        account.put("facebookUrl", vendor.get("facebookUrl"));
        account.put("websiteUrl", vendor.get("websiteUrl"));
        account.put("brandDescription", vendor.get("brandDescription"));
        account.put("brandType", vendor.get("brandType"));
        account.put("brandSummary", vendor.get("brandSummary"));
        return ApiResponse.success("Vendor account retrieved successfully", new VendorAccountResponse(account));
    }

    public ApiResponse<MapBackedResponse> loadVendorStallProfile(String authorizationHeader) {
        Map<String, Object> vendor = authenticatedVendor(authorizationHeader);
        if (vendor.containsKey("message")) {
            return ApiResponse.fail(vendor.get("message").toString());
        }

        return ApiResponse.success(
                "Vendor stall profile retrieved successfully",
                new MapBackedResponse(toVendorStallProfile(vendor)));
    }

    @Transactional
    public ApiResponse<MapBackedResponse> saveVendorStallProfile(
            String authorizationHeader,
            VendorStallSaveRequest body) {
        if (body == null) {
            return ApiResponse.fail("Vendor stall profile request is required");
        }

        Map<String, Object> vendor = authenticatedVendor(authorizationHeader);
        if (vendor.containsKey("message")) {
            return ApiResponse.fail(vendor.get("message").toString());
        }

        String validationError = validateVendorProfile(body);
        if (validationError != null) {
            return ApiResponse.fail(validationError);
        }

        if (body.getProducts() == null) {
            return ApiResponse.fail("Vendor products are required");
        }
        if (body.getProducts().size() > 3) {
            return ApiResponse.fail("Vendor products must not exceed 3 items");
        }
        List<Map<String, Object>> productSnapshots = new ArrayList<>();
        Set<Long> submittedProductIds = new HashSet<>();
        for (VendorProductSaveRequest product : body.getProducts()) {
            String productValidationError = validateVendorProduct(product);
            if (productValidationError != null) {
                return ApiResponse.fail(productValidationError);
            }
            if (product.getId() != null && !submittedProductIds.add(product.getId())) {
                return ApiResponse.fail("Duplicate product id");
            }
            productSnapshots.add(productMap(product));
        }

        Long categoryId = stallRepository.findActiveCategoryIdByName(normalizeText(body.getBrandType()))
                .orElse(null);
        if (categoryId == null) {
            return ApiResponse.fail("Brand type is invalid");
        }

        Long userId = toLong(vendor.get("userId"));
        Long vendorProfileId = toLong(vendor.get("vendorProfileId"));
        String email = normalizeText(vendor.get("email"));

        Map<String, Object> profile = orderedMap(
                "brandName", normalizeText(body.getBrandName()),
                "contactName", normalizeText(body.getContactName()),
                "contactPhone", normalizeText(body.getContactPhone()),
                "contactEmail", normalizeText(body.getContactEmail()),
                "city", normalizeText(body.getCity()),
                "district", normalizeText(body.getDistrict()),
                "address", normalizeText(body.getAddress()),
                "categoryId", categoryId,
                "instagramUrl", nullIfBlank(body.getInstagramUrl()),
                "facebookUrl", nullIfBlank(body.getFacebookUrl()),
                "websiteUrl", nullIfBlank(body.getWebsiteUrl()),
                "brandSummary", normalizeText(body.getBrandSummary()),
                "brandDescription", normalizeText(body.getBrandDescription()));

        try {
            int updatedRows = stallRepository.updateVendorProfile(userId, vendorProfileId, profile);
            if (updatedRows == 0) {
                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                return ApiResponse.fail("Vendor profile save failed");
            }
            int savedProducts = stallRepository.replaceVendorProducts(vendorProfileId, productSnapshots);
            if (savedProducts != productSnapshots.size()) {
                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                return ApiResponse.fail("Vendor product snapshot save failed");
            }
            Map<String, Object> refreshedVendor = stallRepository.findVendorAccountByEmail(email)
                    .orElse(vendor);
            return ApiResponse.success(
                    "Vendor stall profile saved successfully",
                    new MapBackedResponse(toVendorStallProfile(refreshedVendor)));
        } catch (RuntimeException exception) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return ApiResponse.fail("Vendor profile save failed");
        }
    }

    public ApiResponse<VendorStallMapResponse> getVendorStallMap(
            String authorizationHeader,
            String applicationNo,
            LocalDate applyDate) {
        String token = jwtService.extractTokenFromAuthorizationHeader(authorizationHeader);
        if (token == null || token.isBlank()) {
            return ApiResponse.fail("Authorization token is required");
        }
        if (!jwtService.isTokenValid(token)) {
            return ApiResponse.fail("Invalid or expired token");
        }
        if (!"VENDOR".equals(jwtService.getRole(token))) {
            return ApiResponse.fail("This account is not a vendor");
        }

        if (applicationNo == null || applicationNo.isBlank()) {
            return ApiResponse.fail("Application number is required");
        }

        String email = jwtService.getEmail(token);
        Map<String, Object> vendor = stallRepository.findVendorAccountByEmail(email)
                .orElse(null);
        if (vendor == null) {
            return ApiResponse.fail("Vendor profile not found");
        }
        if (!"VENDOR".equals(vendor.get("role"))) {
            return ApiResponse.fail("This account is not a vendor");
        }

        LocalDate targetDate = applyDate;
        if (targetDate == null) {
            Map<String, Object> application = stallRepository.findApplicationForSelection(applicationNo).orElse(null);
            if (application == null) {
                return ApiResponse.fail("Application not found");
            }
            List<Map<String, Object>> applicationDates = stallRepository
                    .findApplicationDatesForSelection(((Number) application.get("applicationId")).longValue());
            targetDate = applicationDates.stream()
                    .map(date -> toLocalDate(date.get("applyDate")))
                    .filter(date -> date != null)
                    .min(LocalDate::compareTo)
                    .orElse(null);
        }
        if (targetDate == null) {
            return ApiResponse.fail("Apply date is required");
        }

        Map<String, Object> applicationData = stallRepository.findVendorStallMapApplication(applicationNo, targetDate)
                .orElse(null);
        if (applicationData == null) {
            return ApiResponse.fail("Application not found");
        }
        if (applicationData.get("currentApplyDate") == null) {
            return ApiResponse.fail("Apply date is not part of this application");
        }

        Long vendorUserId = ((Number) vendor.get("userId")).longValue();
        Long applicationUserId = ((Number) applicationData.get("userId")).longValue();
        if (!vendorUserId.equals(applicationUserId)) {
            return ApiResponse.fail("Application does not belong to this account");
        }

        String applicationStatus = applicationStatusService.resolveApplicationStatus(applicationData);
        if (!canViewStallMap(applicationStatus, applicationData)) {
            return ApiResponse.fail("Application is not selectable for stall map");
        }

        Long eventId = ((Number) applicationData.get("eventId")).longValue();
        List<Map<String, Object>> stalls = stallRepository.findEventStallsMap(eventId, targetDate).stream()
                .map(this::withDisplayBoothStatus)
                .toList();
        List<Map<String, Object>> selectedStalls = stallRepository.findSelectedApplicationDates(applicationNo).stream()
                .map(this::selectedStallSummary)
                .toList();
        List<Object> alreadySelectDate = selectedStalls.stream()
                .map(stall -> stall.get("applyDate"))
                .toList();

        Map<String, Object> application = new LinkedHashMap<>();
        application.put("applicationNo", applicationData.get("applicationNo"));
        application.put("applicationStatus", applicationStatus);
        application.put("vendorName", applicationData.get("vendorName"));
        application.put("currentApplyDate", targetDate);
        application.put("applyDates", applicationData.get("applyDates"));
        application.put("applyDateCount", applicationData.get("applicationDateCount"));
        application.put("selectedStalls", selectedStalls);
        application.put("alreadyselectdate", alreadySelectDate);

        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventTitle", applicationData.get("eventTitle"));
        event.put("startAt", applicationData.get("startAt"));
        event.put("endAt", applicationData.get("endAt"));
        event.put("address", joinAddress(
                applicationData.get("city"),
                applicationData.get("district"),
                applicationData.get("address")));

        return ApiResponse.success("Vendor stall map retrieved successfully", new VendorStallMapResponse(application, event, stalls));
    }

    public ApiResponse<MapBackedResponse> getOrganizerStallMap(
            String authorizationHeader,
            Long eventId,
            LocalDate applyDate,
            String keyword,
            String status) {
        Map<String, Object> organizer = authenticatedOrganizer(authorizationHeader);
        if (organizer.containsKey("message")) {
            return ApiResponse.fail(organizer.get("message").toString());
        }
        if (eventId == null) {
            return ApiResponse.fail("Event id is required");
        }

        Long organizerUserId = ((Number) organizer.get("userId")).longValue();
        Map<String, Object> eventData = stallRepository.findOrganizerStallMapEvent(organizerUserId, eventId)
                .orElse(null);
        if (eventData == null) {
            return ApiResponse.fail("Event not found");
        }

        LocalDate targetDate = applyDate == null ? toLocalDate(eventData.get("startAt")) : applyDate;
        if (targetDate == null) {
            return ApiResponse.fail("Apply date is required");
        }
        if (!isDateInEventRange(targetDate, eventData)) {
            return ApiResponse.fail("Apply date is not part of this event");
        }

        List<Map<String, Object>> allStallRows = stallRepository.findEventStallsMap(eventId, targetDate);
        long selectedStallCount = allStallRows.stream()
                .filter(stall -> "SELECTED".equals(stringValue(stall.get("status"))))
                .count();
        long availableStallCount = allStallRows.stream()
                .filter(stall -> "AVAILABLE".equals(stringValue(stall.get("status"))))
                .count();
        if (allStallRows.isEmpty()) {
            availableStallCount = Math.max(0L, toLong(eventData.get("totalStallCount")) - selectedStallCount);
        }

        List<Map<String, Object>> stallRows = allStallRows.stream()
                .map(this::withDisplayBoothStatus)
                .filter(stall -> matchesStallKeyword(stall, keyword))
                .filter(stall -> matchesStallStatus(stall, status))
                .toList();

        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventId", eventData.get("eventId"));
        event.put("eventTitle", eventData.get("eventTitle"));
        event.put("locationName", eventData.get("locationName"));
        event.put("eventStatus", displayOrganizerEventStatus(eventData));
        event.put("statusNote", displayRegistrationProgress(eventData));
        event.put("totalStallCount", eventData.get("totalStallCount"));
        event.put("selectedStallCount", selectedStallCount);
        event.put("availableStallCount", availableStallCount);
        event.put("startAt", eventData.get("startAt"));
        event.put("endAt", eventData.get("endAt"));
        event.put("currentApplyDate", targetDate);
        event.put("address", joinAddress(
                eventData.get("city"),
                eventData.get("district"),
                eventData.get("address")));
        event.put("mapImageUrl", eventData.get("mapImageUrl"));

        return ApiResponse.success(
                "Organizer stall map retrieved successfully",
                new MapBackedResponse(orderedMap(
                        "event", event,
                        "stalls", groupStallsByZone(stallRows))));
    }

    public ApiResponse<MapBackedResponse> getOrganizerStallMapDetail(
            String authorizationHeader,
            Long eventId,
            String stallNo,
            LocalDate applyDate) {
        Map<String, Object> organizer = authenticatedOrganizer(authorizationHeader);
        if (organizer.containsKey("message")) {
            return ApiResponse.fail(organizer.get("message").toString());
        }
        if (eventId == null) {
            return ApiResponse.fail("Event id is required");
        }
        if (stallNo == null || stallNo.isBlank()) {
            return ApiResponse.fail("Stall number is required");
        }

        Long organizerUserId = ((Number) organizer.get("userId")).longValue();
        Map<String, Object> eventData = stallRepository.findOrganizerStallMapEvent(organizerUserId, eventId)
                .orElse(null);
        if (eventData == null) {
            return ApiResponse.fail("Event not found");
        }
        LocalDate targetDate = applyDate == null ? toLocalDate(eventData.get("startAt")) : applyDate;
        if (targetDate == null) {
            return ApiResponse.fail("Apply date is required");
        }
        if (!isDateInEventRange(targetDate, eventData)) {
            return ApiResponse.fail("Apply date is not part of this event");
        }
        Map<String, Object> detail = stallRepository
                .findOrganizerStallMapDetail(organizerUserId, eventId, stallNo, targetDate)
                .orElse(null);
        if (detail == null) {
            return ApiResponse.fail("Stall not found");
        }

        Map<String, Object> applicationData = applicationData(detail);

        Map<String, Object> stall = new LinkedHashMap<>();
        stall.put("stallId", detail.get("stallId"));
        stall.put("stallNo", detail.get("stallNo"));
        stall.put("zoneId", detail.get("zoneId"));
        stall.put("zoneName", detail.get("zoneName"));
        stall.put("width", detail.get("width"));
        stall.put("length", detail.get("length"));
        stall.put("status", displayBoothStatus(detail.get("stallStatus")));
        stall.put("applyDate", targetDate);
        stall.put("selectedAt", detail.get("selectedAt"));

        Map<String, Object> response = orderedMap(
                "stall", stall,
                "application", applicationData == null ? null : orderedMap(
                        "id", detail.get("applicationId")),
                "vendor", applicationData == null ? null : orderedMap(
                        "brandName", detail.get("brandName"),
                        "brandType", detail.get("categoryName"),
                        "vendorOwnerName", detail.get("vendorOwnerName"),
                        "vendorPhone", detail.get("vendorPhone"),
                        "vendorEmail", detail.get("vendorEmail")));

        return ApiResponse.success(
                "Organizer stall detail retrieved successfully",
                new MapBackedResponse(response));
    }

    private boolean canViewStallMap(String applicationStatus, Map<String, Object> applicationData) {
        return "PAID".equals(stringValue(applicationData.get("paymentStatus")))
                && !isTrue(applicationData.get("isCancelled"))
                && stringValue(applicationData.get("refundStatus")).isEmpty();
    }

    private Map<String, Object> authenticatedVendor(String authorizationHeader) {
        String token = jwtService.extractTokenFromAuthorizationHeader(authorizationHeader);
        if (token == null || token.isBlank()) {
            return Map.of("message", "Authorization token is required");
        }
        if (!jwtService.isTokenValid(token)) {
            return Map.of("message", "Invalid or expired token");
        }
        if (!"VENDOR".equals(jwtService.getRole(token))) {
            return Map.of("message", "This account is not a vendor");
        }

        Map<String, Object> vendor = stallRepository.findVendorAccountByEmail(jwtService.getEmail(token))
                .orElse(null);
        if (vendor == null) {
            return Map.of("message", "Vendor profile not found");
        }
        if (!"VENDOR".equals(vendor.get("role"))) {
            return Map.of("message", "This account is not a vendor");
        }
        return vendor;
    }

    private Map<String, Object> toVendorStallProfile(Map<String, Object> vendor) {
        Long vendorProfileId = toLong(vendor.get("vendorProfileId"));
        return orderedMap(
                "brandName", vendor.get("name"),
                "contactName", vendor.get("contactName"),
                "contactPhone", vendor.get("contactPhone"),
                "contactEmail", vendor.get("contactEmail"),
                "city", vendor.get("city"),
                "district", vendor.get("district"),
                "address", vendor.get("address"),
                "instagramUrl", vendor.get("instagramUrl"),
                "facebookUrl", vendor.get("facebookUrl"),
                "websiteUrl", vendor.get("websiteUrl"),
                "avatarImageUrl", vendor.get("avatarImageUrl"),
                "coverImageUrl", vendor.get("coverImageUrl"),
                "brandSummary", vendor.get("brandSummary"),
                "brandDescription", vendor.get("brandDescription"),
                "brandType", vendor.get("brandType"),
                "products", stallRepository.findVendorProducts(vendorProfileId));
    }

    private String validateVendorProfile(VendorStallSaveRequest body) {
        if (normalizeText(body.getBrandName()).isEmpty()) {
            return "Brand name is required";
        }
        if (normalizeText(body.getBrandName()).length() > 150) {
            return "Brand name must not exceed 150 characters";
        }
        if (normalizeText(body.getContactName()).isEmpty()) {
            return "Contact name is required";
        }
        if (normalizeText(body.getContactName()).length() > 100) {
            return "Contact name must not exceed 100 characters";
        }
        if (!TAIWAN_MOBILE_PATTERN.matcher(normalizeText(body.getContactPhone())).matches()) {
            return "Contact phone must be 10 digits and start with 09";
        }
        String contactEmail = normalizeText(body.getContactEmail());
        if (contactEmail.isEmpty()) {
            return "Contact email is required";
        }
        if (contactEmail.length() > 255 || !EMAIL_PATTERN.matcher(contactEmail).matches()) {
            return "Invalid contact email format";
        }
        String city = normalizeText(body.getCity());
        if (city.isEmpty()) {
            return "City is required";
        }
        if (city.length() > 50 || !taiwanAddressService.isValidCity(city)) {
            return "City is invalid";
        }
        String district = normalizeText(body.getDistrict());
        if (district.isEmpty()) {
            return "District is required";
        }
        if (district.length() > 50 || !taiwanAddressService.isValidDistrict(city, district)) {
            return "District is invalid for city";
        }
        if (normalizeText(body.getAddress()).isEmpty()) {
            return "Address is required";
        }
        if (normalizeText(body.getAddress()).length() > 255) {
            return "Address must not exceed 255 characters";
        }
        if (normalizeText(body.getBrandSummary()).isEmpty()) {
            return "Brand summary is required";
        }
        if (normalizeText(body.getBrandSummary()).length() > 300) {
            return "Brand summary must not exceed 300 characters";
        }
        if (normalizeText(body.getBrandDescription()).isEmpty()) {
            return "Brand description is required";
        }
        if (normalizeText(body.getBrandType()).isEmpty()) {
            return "Brand type is required";
        }
        return null;
    }

    private String validateVendorProduct(VendorProductSaveRequest product) {
        if (product == null) {
            return "Vendor product request is required";
        }
        if (product.getId() != null && product.getId() <= 0) {
            return "Product id is invalid";
        }
        if (normalizeText(product.getProductName()).isEmpty()) {
            return "Product name is required";
        }
        if (normalizeText(product.getProductName()).length() > 150) {
            return "Product name must not exceed 150 characters";
        }
        if (normalizeText(product.getProductSummary()).isEmpty()) {
            return "Product summary is required";
        }
        if (normalizeText(product.getProductSummary()).length() > 255) {
            return "Product summary must not exceed 255 characters";
        }
        BigDecimal price = product.getProductPrice();
        if (price == null) {
            return "Product price is required";
        }
        if (price.compareTo(BigDecimal.ZERO) < 0) {
            return "Product price must be greater than or equal to 0";
        }
        return null;
    }

    private Map<String, Object> productMap(VendorProductSaveRequest product) {
        return orderedMap(
                "id", product.getId(),
                "productName", normalizeText(product.getProductName()),
                "productSummary", normalizeText(product.getProductSummary()),
                "productPrice", product.getProductPrice(),
                "productImageUrl", nullIfBlank(product.getProductImageUrl()));
    }

    private String nullIfBlank(Object value) {
        String text = normalizeText(value);
        return text.isEmpty() ? null : text;
    }

    private Map<String, Object> authenticatedOrganizer(String authorizationHeader) {
        String token = jwtService.extractTokenFromAuthorizationHeader(authorizationHeader);
        if (token == null || token.isBlank()) {
            return Map.of("message", "Authorization token is required");
        }
        if (!jwtService.isTokenValid(token)) {
            return Map.of("message", "Invalid or expired token");
        }
        if (!"ORGANIZER".equals(jwtService.getRole(token))) {
            return Map.of("message", "This account is not an organizer");
        }

        Map<String, Object> organizer = organizerRepository.findOrganizerAccountByEmail(jwtService.getEmail(token))
                .orElse(null);
        if (organizer == null) {
            return Map.of("message", "Organizer profile not found");
        }
        if (!"ORGANIZER".equals(organizer.get("role"))) {
            return Map.of("message", "This account is not an organizer");
        }
        return organizer;
    }

    private Map<String, Object> applicationData(Map<String, Object> detail) {
        if (detail.get("applicationId") == null) {
            return null;
        }
        return orderedMap(
                "reviewStatus", detail.get("reviewStatus"),
                "paymentStatus", detail.get("paymentStatus"),
                "depositStatus", detail.get("depositStatus"),
                "isCancelled", detail.get("isCancelled"),
                "selectedStallId", detail.get("stallId"),
                "eventEndAt", null,
                "refundStatus", detail.get("refundStatus"));
    }

    private String paymentStatusText(Object status) {
        return switch (stringValue(status)) {
            case "PAID" -> "付款完成";
            case "PENDING" -> "待付款";
            case "FAILED" -> "付款失敗";
            case "EXPIRED" -> "付款逾期";
            default -> normalizeText(status);
        };
    }

    private Map<String, Object> withDisplayBoothStatus(Map<String, Object> stall) {
        Map<String, Object> response = new LinkedHashMap<>(stall);
        response.put("status", displayBoothStatus(stall.get("status")));
        if (stall.get("selectedApplicationId") != null) {
            response.put("selectedVendor", orderedMap(
                    "name", stall.get("vendorName"),
                    "type", stall.get("brandType"),
                    "ownerName", stall.get("vendorOwnerName"),
                    "selectedAt", stall.get("selectedAt")));
        }
        response.remove("vendorName");
        response.remove("brandType");
        response.remove("vendorOwnerName");
        response.remove("selectedAt");
        return response;
    }

    private Map<String, Object> withCurrentApplyDate(Map<String, Object> stall, LocalDate applyDate) {
        Map<String, Object> response = new LinkedHashMap<>(stall);
        response.put("currentApplyDate", applyDate);
        return response;
    }

    @SuppressWarnings("unchecked")
    private boolean matchesStallKeyword(Map<String, Object> stall, String keyword) {
        String normalizedKeyword = normalizeText(keyword).toLowerCase();
        if (normalizedKeyword.isEmpty()) {
            return true;
        }
        String stallNo = normalizeText(stall.get("stallNo")).toLowerCase();
        if (stallNo.contains(normalizedKeyword)) {
            return true;
        }
        Object selectedVendor = stall.get("selectedVendor");
        if (selectedVendor instanceof Map<?, ?> vendor) {
            String vendorName = normalizeText(((Map<String, Object>) vendor).get("name")).toLowerCase();
            return vendorName.contains(normalizedKeyword);
        }
        return false;
    }

    private boolean matchesStallStatus(Map<String, Object> stall, String status) {
        String normalizedStatus = normalizeText(status);
        if (normalizedStatus.isEmpty()
                || "全部".equals(normalizedStatus)
                || "全部狀態".equals(normalizedStatus)) {
            return true;
        }

        String displayStatus = normalizeText(stall.get("status"));
        String rawStatus = switch (normalizedStatus.toUpperCase()) {
            case "AVAILABLE" -> "可選擇";
            case "SELECTED" -> "已選擇";
            case "ASSIGNED", "SOLD" -> "系統分配";
            case "DISABLED" -> "不可使用";
            default -> normalizedStatus;
        };
        return displayStatus.equals(rawStatus);
    }

    private List<Map<String, Object>> groupStallsByZone(List<Map<String, Object>> stalls) {
        Map<String, Map<String, Object>> zones = new LinkedHashMap<>();
        for (Map<String, Object> stall : stalls) {
            String zoneName = normalizeText(stall.get("zoneName"));
            if (zoneName.isEmpty()) {
                zoneName = "未分區";
            }
            Map<String, Object> zone = zones.computeIfAbsent(zoneName, key -> orderedMap(
                    "zoneName", key,
                    "zoneId", stall.get("zoneId"),
                    "stalls", new java.util.ArrayList<Map<String, Object>>()));

            Map<String, Object> stallData = new LinkedHashMap<>(stall);
            stallData.remove("zoneName");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> zoneStalls = (List<Map<String, Object>>) zone.get("stalls");
            zoneStalls.add(stallData);
        }
        return List.copyOf(zones.values());
    }

    private String displayBoothStatus(Object status) {
        return switch (stringValue(status)) {
            case "AVAILABLE" -> "可選擇";
            case "SELECTED" -> "已選擇";
            case "ASSIGNED", "SOLD" -> "系統分配";
            case "DISABLED" -> "不可使用";
            default -> normalizeText(status);
        };
    }

    private String displayOrganizerEventStatus(Map<String, Object> eventData) {
        String workflowStatus = stringValue(eventData.get("workflowStatus"));
        if ("UNPUBLISHED".equals(workflowStatus) || "CANCELLED".equals(workflowStatus)) {
            return "已下架";
        }
        if ("READY_TO_PUBLISH".equals(workflowStatus)) {
            return "待發布";
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime registrationStartAt = toLocalDateTime(eventData.get("registrationStartAt"));
        LocalDateTime registrationEndAt = toLocalDateTime(eventData.get("registrationEndAt"));
        LocalDateTime brandsPublicAt = toLocalDateTime(eventData.get("brandsPublicAt"));
        LocalDateTime startAt = toLocalDateTime(eventData.get("startAt"));
        LocalDateTime endAt = toLocalDateTime(eventData.get("endAt"));

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
            return isOrganizerEventFull(eventData) ? "已額滿" : "報名中";
        }
        if (brandsPublicAt == null || !now.isBefore(brandsPublicAt)) {
            return "品牌已公開";
        }
        return "品牌已公開";
    }

    private boolean isOrganizerEventFull(Map<String, Object> eventData) {
        return isTrue(eventData.get("isFullySelected"));
    }

    private String displayRegistrationProgress(Map<String, Object> eventData) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime registrationStartAt = toLocalDateTime(eventData.get("registrationStartAt"));
        LocalDateTime registrationEndAt = toLocalDateTime(eventData.get("registrationEndAt"));

        if (registrationStartAt != null && now.isBefore(registrationStartAt)) {
            return "\u672a\u958b\u59cb\u5831\u540d";
        }
        if (registrationEndAt != null && now.isAfter(registrationEndAt)) {
            return "\u5831\u540d\u622a\u6b62";
        }
        return "\u5831\u540d\u4e2d";
    }

    private boolean isSelectedStallApplication(Map<String, Object> applicationData) {
        return toLong(applicationData.get("selectedStallCount")) > 0
                && toLong(applicationData.get("selectedStallCount")).equals(toLong(applicationData.get("applicationDateCount")))
                && "PAID".equals(stringValue(applicationData.get("paymentStatus")))
                && !isTrue(applicationData.get("isCancelled"))
                && stringValue(applicationData.get("refundStatus")).isEmpty();
    }

    private Map<String, Object> selectedStallSummary(Map<String, Object> selectedDate) {
        Map<String, Object> stall = new LinkedHashMap<>();
        stall.put("selectedStallId", selectedDate.get("selectedStallId"));
        stall.put("applyDate", selectedDate.get("applyDate"));
        stall.put("stallNo", selectedDate.get("stallNo"));
        stall.put("zoneName", selectedDate.get("zoneName"));
        stall.put("width", selectedDate.get("width"));
        stall.put("length", selectedDate.get("length"));
        return stall;
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
        if (!text.isEmpty()) {
            builder.append(text);
        }
    }

    private String normalizeText(Object value) {
        if (value == null) {
            return "";
        }
        return value.toString().trim();
    }

    private boolean isTrue(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof Number numberValue) {
            return numberValue.intValue() == 1;
        }
        String text = stringValue(value);
        return "TRUE".equals(text) || "1".equals(text);
    }

    private String stringValue(Object value) {
        return value == null ? "" : value.toString().trim().toUpperCase();
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

    private LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        String text = value == null ? "" : value.toString().trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return LocalDateTime.parse(text.replace(" ", "T"));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private boolean isDateInEventRange(LocalDate date, Map<String, Object> eventData) {
        LocalDate startDate = toLocalDate(eventData.get("startAt"));
        LocalDate endDate = toLocalDate(eventData.get("endAt"));
        if (date == null || startDate == null || endDate == null) {
            return false;
        }
        return !date.isBefore(startDate) && !date.isAfter(endDate);
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String string && !string.isBlank()) {
            return Long.valueOf(string);
        }
        return 0L;
    }

    private Map<String, Object> orderedMap(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            map.put(keyValues[i].toString(), keyValues[i + 1]);
        }
        return map;
    }
}
