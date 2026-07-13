package com.example.demo.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
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
import com.example.demo.dto.request.VendorApplicationSubmitRequest;
import com.example.demo.dto.response.ApiResponse;
import com.example.demo.dto.response.EventStallStatusResponse;
import com.example.demo.dto.response.MapBackedResponse;
import com.example.demo.dto.response.MarketSearchResponse;
import com.example.demo.dto.response.MarketSummaryResponse;
import com.example.demo.dto.response.PageResponse;
import com.example.demo.dto.response.StallSelectionResponse;
import com.example.demo.dto.response.VendorApplicationSubmitResponse;
import com.example.demo.dto.response.VendorAccountResponse;
import com.example.demo.dto.response.VendorMarketDetailResponse;
import com.example.demo.dto.response.VendorMarketSearchResponse;
import com.example.demo.dto.response.VendorMarketSummaryResponse;
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

        int updatedRows = stallRepository.updateVendorProfile(userId, vendorProfileId, profile);
        if (updatedRows == 0) {
            return ApiResponse.fail("Vendor profile save failed");
        }
        stallRepository.saveVendorImage(vendorProfileId, "AVATAR", nullIfBlank(body.getAvatarImageUrl()));
        stallRepository.saveVendorImage(vendorProfileId, "COVER", nullIfBlank(body.getCoverImageUrl()));

        Map<String, Object> refreshedVendor = stallRepository
                .findVendorAccountByEmail(normalizeText(vendor.get("email")))
                .orElse(vendor);
        return ApiResponse.success(
                "Vendor stall profile saved successfully",
                new MapBackedResponse(toVendorStallProfile(refreshedVendor)));
    }

    @Transactional
    public ApiResponse<MapBackedResponse> addVendorProduct(
            String authorizationHeader,
            VendorProductSaveRequest body) {
        if (body == null) {
            return ApiResponse.fail("Vendor product request is required");
        }
        Map<String, Object> vendor = authenticatedVendor(authorizationHeader);
        if (vendor.containsKey("message")) {
            return ApiResponse.fail(vendor.get("message").toString());
        }
        String validationError = validateVendorProduct(body);
        if (validationError != null) {
            return ApiResponse.fail(validationError);
        }

        Long vendorProfileId = toLong(vendor.get("vendorProfileId"));
        Long productId = stallRepository.createVendorProduct(vendorProfileId, productMap(body));
        if (productId == null) {
            return ApiResponse.fail("Vendor product save failed");
        }
        Map<String, Object> product = stallRepository.findVendorProduct(vendorProfileId, productId).orElse(null);
        return ApiResponse.success(
                "Vendor product saved successfully",
                new MapBackedResponse(orderedMap("product", product)));
    }

    @Transactional
    public ApiResponse<MapBackedResponse> deleteVendorProduct(
            String authorizationHeader,
            Long productId) {
        if (productId == null) {
            return ApiResponse.fail("Product id is required");
        }
        Map<String, Object> vendor = authenticatedVendor(authorizationHeader);
        if (vendor.containsKey("message")) {
            return ApiResponse.fail(vendor.get("message").toString());
        }

        Long vendorProfileId = toLong(vendor.get("vendorProfileId"));
        Map<String, Object> existingProduct = stallRepository.findVendorProduct(vendorProfileId, productId)
                .orElse(null);
        if (existingProduct == null || !"ACTIVE".equals(stringValue(existingProduct.get("status")))) {
            return ApiResponse.fail("Vendor product not found");
        }

        int updatedRows = stallRepository.hideVendorProduct(vendorProfileId, productId);
        if (updatedRows == 0) {
            return ApiResponse.fail("Vendor product delete failed");
        }

        return ApiResponse.success(
                "Vendor product deleted successfully",
                new MapBackedResponse(orderedMap(
                        "productId", productId,
                        "status", "HIDDEN")));
    }

    @Transactional
    public ApiResponse<MapBackedResponse> editVendorProduct(
            String authorizationHeader,
            Long productId,
            VendorProductSaveRequest body) {
        if (productId == null) {
            return ApiResponse.fail("Product id is required");
        }
        if (body == null) {
            return ApiResponse.fail("Vendor product request is required");
        }
        Map<String, Object> vendor = authenticatedVendor(authorizationHeader);
        if (vendor.containsKey("message")) {
            return ApiResponse.fail(vendor.get("message").toString());
        }

        Long vendorProfileId = toLong(vendor.get("vendorProfileId"));
        Map<String, Object> existingProduct = stallRepository.findVendorProduct(vendorProfileId, productId)
                .orElse(null);
        if (existingProduct == null) {
            return ApiResponse.fail("Vendor product not found");
        }

        String validationError = validateVendorProduct(body);
        if (validationError != null) {
            return ApiResponse.fail(validationError);
        }
        int updatedRows = stallRepository.updateVendorProduct(vendorProfileId, productId, productMap(body));
        if (updatedRows == 0) {
            return ApiResponse.fail("Vendor product save failed");
        }

        Map<String, Object> product = stallRepository.findVendorProduct(vendorProfileId, productId)
                .orElse(existingProduct);
        return ApiResponse.success(
                "Vendor product saved successfully",
                new MapBackedResponse(orderedMap("product", product)));
    }

    /**
     * 攤主送出活動報名。
     * 
     * 流程：
     * 1. 驗證 JWT 並確認登入者是攤主。
     * 2. 確認活動已發布、目前仍在報名期間，且報名日期落在活動期間內。
     * 3. 計算報名費與租借設備費用。
     * 4. 寫入 event_applications、application_dates、equipment_rentals 與
     * rental_appliances。
     */
    @Transactional
    public ApiResponse<VendorApplicationSubmitResponse> submitVendorApplication(
            String authorizationHeader,
            VendorApplicationSubmitRequest body) {
        Map<String, Object> vendor = authenticatedVendor(authorizationHeader);
        if (vendor.containsKey("message")) {
            return ApiResponse.fail(vendor.get("message").toString());
        }
        if (body == null || body.getEventId() == null) {
            return ApiResponse.fail("Event id is required");
        }

        Map<String, Object> event = stallRepository.findMarketEventForApplication(body.getEventId()).orElse(null);
        if (event == null) {
            return ApiResponse.fail("Event not found");
        }
        String workflowStatus = stringValue(event.get("workflowStatus"));
        if (!"PUBLISHED".equals(workflowStatus)) {
            return ApiResponse.fail("活動尚未開放報名");
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime registrationStartAt = toLocalDateTime(event.get("registrationStartAt"));
        LocalDateTime registrationEndAt = toLocalDateTime(event.get("registrationEndAt"));
        if (registrationStartAt != null && now.isBefore(registrationStartAt)) {
            return ApiResponse.fail("活動報名尚未開始");
        }
        if (registrationEndAt != null && now.isAfter(registrationEndAt)) {
            return ApiResponse.fail("活動報名已結束");
        }

        List<LocalDate> applyDates = normalizedApplyDates(body.getApplyDates());
        if (applyDates.isEmpty()) {
            return ApiResponse.fail("Apply dates are required");
        }
        for (LocalDate applyDate : applyDates) {
            if (!isDateInEventRange(applyDate, event)) {
                return ApiResponse.fail("Apply date is not part of this event");
            }
        }

        Long vendorUserId = ((Number) vendor.get("userId")).longValue();
        Long vendorProfileId = ((Number) vendor.get("vendorProfileId")).longValue();
        if (stallRepository.existsVendorApplication(body.getEventId(), vendorProfileId)) {
            return ApiResponse.fail("此活動已建立報名資料");
        }

        List<Map<String, Object>> rentalResponses = new ArrayList<>();
        BigDecimal equipmentTotal = BigDecimal.ZERO;
        List<PreparedEquipmentRental> preparedRentals = new ArrayList<>();
        List<VendorApplicationSubmitRequest.EquipmentRental> requestedRentals = body.getEquipmentRentals() == null
                ? List.of()
                : body.getEquipmentRentals();
        for (VendorApplicationSubmitRequest.EquipmentRental rental : requestedRentals) {
            if (rental == null || rental.getEventEquipmentId() == null) {
                return ApiResponse.fail("請提供活動設備 ID");
            }
            Map<String, Object> equipment = stallRepository
                    .findEventEquipmentForApplication(body.getEventId(), rental.getEventEquipmentId())
                    .orElse(null);
            if (equipment == null) {
                return ApiResponse.fail("找不到活動設備資料");
            }
            if (!"ACTIVE".equals(stringValue(equipment.get("rentalStatus")))) {
                return ApiResponse.fail("活動設備目前不可租借");
            }

            int quantity = rental.getQuantity() == null ? 1 : rental.getQuantity();
            int rentalUnits = rental.getRentalUnits() == null ? applyDates.size() : rental.getRentalUnits();
            if (quantity < 1 || rentalUnits < 1) {
                return ApiResponse.fail("設備租借數量與租借單位數必須大於 0");
            }

            Integer stockQuantity = toInteger(equipment.get("stockQuantity"));
            Integer perStallRentalLimit = toInteger(equipment.get("perStallRentalLimit"));
            if (stockQuantity != null && quantity > stockQuantity) {
                return ApiResponse.fail("設備租借數量超過可租借庫存");
            }
            if (perStallRentalLimit != null && quantity > perStallRentalLimit) {
                return ApiResponse.fail("設備租借數量超過單攤租借上限");
            }

            BigDecimal rentalFee = toBigDecimal(equipment.get("rentalFee"));
            if ("FREE".equals(stringValue(equipment.get("chargeType")))) {
                rentalFee = BigDecimal.ZERO;
            }
            BigDecimal subtotal = rentalFee
                    .multiply(BigDecimal.valueOf(quantity))
                    .multiply(BigDecimal.valueOf(rentalUnits));
            equipmentTotal = equipmentTotal.add(subtotal);

            PreparedEquipmentRental preparedRental = new PreparedEquipmentRental(
                    rental.getEventEquipmentId(),
                    normalizeText(equipment.get("name")),
                    rentalFee,
                    normalizeText(equipment.get("pricingUnit")),
                    blankToNull(normalizeText(equipment.get("unit"))),
                    quantity,
                    rentalUnits,
                    subtotal,
                    rental.getAppliances() == null ? List.of() : rental.getAppliances());
            preparedRentals.add(preparedRental);
            rentalResponses.add(orderedMap(
                    "eventEquipmentId", preparedRental.eventEquipmentId(),
                    "equipmentName", preparedRental.equipmentName(),
                    "rentalFee", preparedRental.rentalFee(),
                    "pricingUnit", preparedRental.pricingUnit(),
                    "unit", preparedRental.unit(),
                    "quantity", preparedRental.quantity(),
                    "rentalUnits", preparedRental.rentalUnits(),
                    "subtotal", preparedRental.subtotal()));
        }

        BigDecimal baseFee = toBigDecimal(event.get("baseFee"));
        BigDecimal applicationFee = baseFee.multiply(BigDecimal.valueOf(applyDates.size()));
        BigDecimal depositAmount = BigDecimal.ZERO;
        BigDecimal totalAmount = applicationFee.add(equipmentTotal).add(depositAmount);
        String applicationNo = nextApplicationNo(event);
        LocalDateTime paymentDueAt = now.plusDays(3);

        try {
            Long applicationId = stallRepository.createEventApplication(
                    applicationNo,
                    body.getEventId(),
                    vendorUserId,
                    vendorProfileId,
                    blankToNull(body.getVehicleNo()),
                    blankToNull(body.getApplicantNote()),
                    totalAmount,
                    depositAmount,
                    paymentDueAt);
            for (LocalDate applyDate : applyDates) {
                stallRepository.createApplicationDate(applicationId, applyDate);
            }
            for (PreparedEquipmentRental rental : preparedRentals) {
                Long equipmentRentalId = stallRepository.createEquipmentRental(
                        applicationId,
                        rental.eventEquipmentId(),
                        rental.equipmentName(),
                        rental.rentalFee(),
                        rental.pricingUnit(),
                        rental.unit(),
                        rental.quantity(),
                        rental.rentalUnits(),
                        rental.subtotal());
                for (VendorApplicationSubmitRequest.Appliance appliance : rental.appliances()) {
                    stallRepository.createRentalAppliance(
                            equipmentRentalId,
                            appliance.getApplianceName().trim(),
                            appliance.getWattage());
                }
            }

            Map<String, Object> response = orderedMap(
                    "applicationId", applicationId,
                    "applicationNo", applicationNo,
                    "eventId", body.getEventId(),
                    "eventTitle", event.get("eventTitle"),
                    "applicationStatus", "待審核",
                    "reviewStatus", "PENDING",
                    "paymentStatus", "PENDING",
                    "applyDates", applyDates,
                    "applicationFee", applicationFee,
                    "equipmentTotal", equipmentTotal,
                    "depositAmount", depositAmount,
                    "totalAmount", totalAmount,
                    "paymentDueAt", paymentDueAt,
                    "equipmentRentals", rentalResponses);
            return ApiResponse.success(
                    "活動報名送出成功",
                    new VendorApplicationSubmitResponse(response));
        } catch (DataIntegrityViolationException exception) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return ApiResponse.fail("此活動已建立報名資料");
        }
    }

    /**
     * 查詢攤主後台「我的報名紀錄」列表。
     *
     * <p>
     * 流程：
     * 1. 驗證 JWT 並確認登入者是攤主。
     * 2. 依攤主 userId 查詢自己的報名紀錄。
     * 3. 使用 ApplicationStatusService 統一推導顯示狀態。
     * 4. 依狀態分頁籤篩選並回傳分頁結果。
     * </p>
     */
    public ApiResponse<VendorMarketSearchResponse> searchVendorMarkets(
            String authorizationHeader,
            String eventTitle,
            String applicationNo,
            String status,
            LocalDate eventStartAt,
            LocalDate eventEndAt,
            Integer page,
            Integer pageSize) {
        Map<String, Object> vendor = authenticatedVendor(authorizationHeader);
        if (vendor.containsKey("message")) {
            return ApiResponse.fail(vendor.get("message").toString());
        }

        Long vendorUserId = ((Number) vendor.get("userId")).longValue();
        List<VendorMarketSummaryResponse> applications = stallRepository
                .findVendorMarketApplications(vendorUserId, eventTitle, applicationNo, eventStartAt, eventEndAt)
                .stream()
                .map(this::withDisplayApplicationStatus)
                .filter(application -> matchesApplicationStatus(application, status))
                .map(this::toVendorMarketSummaryResponse)
                .map(VendorMarketSummaryResponse::new)
                .toList();

        return ApiResponse.success(
                "Vendor market applications retrieved successfully",
                new VendorMarketSearchResponse(PageResponse.from(applications, page, pageSize)));
    }

    /**
     * 公開查詢活動報名列表，不驗證登入者。
     */
    public ApiResponse<VendorMarketSearchResponse> searchVendorMarkets(
            String eventTitle,
            String applicationNo,
            String status,
            LocalDate eventStartAt,
            LocalDate eventEndAt,
            Integer page,
            Integer pageSize) {
        List<VendorMarketSummaryResponse> applications = stallRepository
                .findVendorMarketApplications(null, eventTitle, applicationNo, eventStartAt, eventEndAt)
                .stream() // 依序對資料執行後面的操作
                .map(this::withDisplayApplicationStatus) // 依序對三筆資料執行後面的操作
                .filter(application -> matchesApplicationStatus(application, status)) // 篩選前端傳入的key = status
                .map(this::toVendorMarketSummaryResponse)
                // toVendorMarketSummaryResponse() 就是負責把資料整理成前端需要的 Response DTO
                .map(VendorMarketSummaryResponse::new)
                .toList();
        // Service 最後包裝回傳結果
        return ApiResponse.success(
                "Vendor market applications retrieved successfully",
                new VendorMarketSearchResponse(PageResponse.from(applications, page, pageSize)));
    }

    /**
     * 取得攤主後台「我的報名紀錄」單筆詳細資料。
     */
    public ApiResponse<VendorMarketDetailResponse> getVendorMarketDetail(
            String authorizationHeader,
            Long applicationId) {
        Map<String, Object> vendor = authenticatedVendor(authorizationHeader);
        if (vendor.containsKey("message")) {
            return ApiResponse.fail(vendor.get("message").toString());
        }
        if (applicationId == null) {
            return ApiResponse.fail("Application id is required");
        }

        Long vendorUserId = ((Number) vendor.get("userId")).longValue();
        Map<String, Object> application = stallRepository
                .findVendorMarketApplicationDetail(vendorUserId, applicationId)
                .orElse(null);
        if (application == null) {
            return ApiResponse.fail("Application not found");
        }

        List<Map<String, Object>> applicationDates = organizerRepository.findApplicationDates(applicationId);
        List<Map<String, Object>> equipmentRentals = organizerRepository.findApplicationEquipmentRentals(applicationId);
        Map<String, Object> response = toVendorMarketDetailResponse(
                withDisplayApplicationStatus(application),
                applicationDates,
                equipmentRentals);

        return ApiResponse.success(
                "Vendor market application detail retrieved successfully",
                new VendorMarketDetailResponse(response));
    }

    /**
     * 取得攤主報名前看到的活動詳細資料，不需要登入。
     * 活動主資料、每日剩餘攤位、設備與交通資訊分開查詢後再組成前端需要的區塊。
     */
    public ApiResponse<VendorMarketDetailResponse> getVendorMarketDetail(Long eventId) {
        if (eventId == null) {
            return ApiResponse.fail("Event id is required");
        }

        Map<String, Object> event = stallRepository.findPublishedMarketDetail(eventId).orElse(null);
        if (event == null) {
            return ApiResponse.fail("Event not found");
        }

        // 使用 LinkedHashMap 固定 JSON 區塊順序，方便前端依畫面區塊直接取用。
        Map<String, Object> response = new LinkedHashMap<>(event);
        response.put("dailyAvailability", stallRepository.findMarketDailyAvailability(eventId));
        response.put("equipments", stallRepository.findPublishedMarketEquipments(eventId));
        response.put("trafficInfos", stallRepository.findPublishedMarketTrafficInfos(eventId));

        return ApiResponse.success(
                "Market event detail retrieved successfully",
                new VendorMarketDetailResponse(response));
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

        return ApiResponse.success("Vendor stall map retrieved successfully",
                new VendorStallMapResponse(application, event, stalls));
    }

    /**
     * 驗證目前登入者是否為攤主，並回傳攤主帳號與品牌資料。
     */
    // private Map<String, Object> authenticatedVendor(String authorizationHeader) {
    // String token =
    // jwtService.extractTokenFromAuthorizationHeader(authorizationHeader);
    // if (token == null || token.isBlank()) {
    // return Map.of("message", "Authorization token is required");
    // }
    // if (!jwtService.isTokenValid(token)) {
    // return Map.of("message", "Invalid or expired token");
    // }
    // if (!"VENDOR".equals(jwtService.getRole(token))) {
    // return Map.of("message", "This account is not a vendor");
    // }

    // Map<String, Object> vendor =
    // stallRepository.findVendorAccountByEmail(jwtService.getEmail(token))
    // .orElse(null);
    // if (vendor == null) {
    // return Map.of("message", "Vendor profile not found");
    // }
    // if (!"VENDOR".equals(vendor.get("role"))) {
    // return Map.of("message", "This account is not a vendor");
    // }
    // return vendor;
    // }

    /**
     * 將 DB 狀態欄位轉成前台顯示用的報名狀態。
     */
    private Map<String, Object> withDisplayApplicationStatus(Map<String, Object> application) {
        Map<String, Object> response = new LinkedHashMap<>(application);
        response.put("applicationStatus", applicationStatusService.resolveApplicationStatus(application));
        return response;
    }

    /**
     * 對應畫面上方的狀態分頁籤；空值、全部、全部狀態都視為不篩選。
     */
    private boolean matchesApplicationStatus(Map<String, Object> application, String status) {
        String normalizedStatus = normalizeText(status);
        if (normalizedStatus.isEmpty()
                || "全部".equals(normalizedStatus)
                || "全部狀態".equals(normalizedStatus)
                || "ALL".equalsIgnoreCase(normalizedStatus)) {
            return true;
        }
        String applicationStatus = normalizeText(application.get("applicationStatus"));
        if (normalizedStatus.equals(applicationStatus)) {
            return true;
        }
        return switch (normalizedStatus.toUpperCase()) {
            case "PENDING", "REVIEW_PENDING" -> "待審核".equals(applicationStatus);
            case "PAYMENT_PENDING", "UNPAID", "FAILED", "EXPIRED" -> "待付款".equals(applicationStatus);
            case "STALL_PENDING", "SELECT_STALL", "WAITING_STALL" -> "待選位".equals(applicationStatus);
            case "COMPLETED", "FINISHED" -> "報名完成".equals(applicationStatus);
            case "REFUND_REQUESTED" -> "退款申請中".equals(applicationStatus);
            case "REFUNDING", "REFUND_FAILED" -> "退款處理中".equals(applicationStatus);
            case "REFUNDED" -> "已退款".equals(applicationStatus);
            case "CANCELLED", "REJECTED" -> "已取消".equals(applicationStatus) || "審核未通過".equals(applicationStatus);
            case "HISTORY" -> isHistoryApplicationStatus(applicationStatus);
            default -> "歷史紀錄".equals(normalizedStatus) && isHistoryApplicationStatus(applicationStatus);
        };
    }

    /**
     * 「歷史紀錄」分頁用來收攏已完成流程或已結束的報名紀錄。
     */
    private boolean isHistoryApplicationStatus(String applicationStatus) {
        return "保證金已退還".equals(applicationStatus)
                || "已取消".equals(applicationStatus)
                || "審核未通過".equals(applicationStatus)
                || "已退款".equals(applicationStatus);
    }

    /**
     * 組成攤主報名列表需要的欄位與操作按鈕旗標。
     */
    private Map<String, Object> toVendorMarketSummaryResponse(Map<String, Object> application) {
        return orderedMap(
                "applicationId", application.get("applicationId"),
                "applicationNo", application.get("applicationNo"),
                "eventId", application.get("eventId"),
                "eventTitle", application.get("eventTitle"),
                "eventDate", formatEventDate(application.get("startAt"), application.get("endAt")),
                "startAt", application.get("startAt"),
                "endAt", application.get("endAt"),
                "locationName", application.get("locationName"),
                "address", joinAddress(
                        application.get("city"),
                        application.get("district"),
                        application.get("address")),
                "imageUrl", application.get("imageUrl"),
                "applicationStatus", application.get("applicationStatus"),
                "reviewStatus", application.get("reviewStatus"),
                "paymentStatus", application.get("paymentStatus"),
                "depositStatus", application.get("depositStatus"),
                "refundStatus", application.get("refundStatus"),
                "isCancelled", application.get("isCancelled"),
                "appliedAt", application.get("appliedAt"),
                "canManagePayment", canManagePayment(application),
                "canSelectStall", canSelectStall(application),
                "canRequestRefund", canRequestRefund(application));
    }

    /**
     * 組成攤主報名詳情頁需要的區塊資料。
     */
    private Map<String, Object> toVendorMarketDetailResponse(
            Map<String, Object> application,
            List<Map<String, Object>> applicationDates,
            List<Map<String, Object>> equipmentRentals) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("application", orderedMap(
                "applicationId", application.get("applicationId"),
                "applicationNo", application.get("applicationNo"),
                "applicationStatus", application.get("applicationStatus"),
                "reviewStatus", application.get("reviewStatus"),
                "paymentStatus", application.get("paymentStatus"),
                "depositStatus", application.get("depositStatus"),
                "refundStatus", application.get("refundStatus"),
                "isCancelled", application.get("isCancelled"),
                "appliedAt", formatDateTimeValue(application.get("appliedAt"))));
        response.put("event", orderedMap(
                "eventId", application.get("eventId"),
                "eventTitle", application.get("eventTitle"),
                "summary", application.get("eventSummary"),
                "description", application.get("eventDescription"),
                "eventDate", formatEventDate(application.get("eventStartAt"), application.get("eventEndAt")),
                "startAt", application.get("eventStartAt"),
                "endAt", application.get("eventEndAt"),
                "registrationStartAt", application.get("registrationStartAt"),
                "registrationEndAt", application.get("registrationEndAt"),
                "locationName", application.get("locationName"),
                "address", joinAddress(
                        application.get("eventCity"),
                        application.get("eventDistrict"),
                        application.get("eventAddress")),
                "coverImageUrl", application.get("eventCoverImageUrl")));
        response.put("brand", orderedMap(
                "vendorProfileId", application.get("vendorProfileId"),
                "brandName", application.get("brandName"),
                "categoryName", application.get("categoryName"),
                "brandType", application.get("brandType"),
                "brandDescription", application.get("brandDescription"),
                "productSummary", application.get("productSummary"),
                "instagramUrl", application.get("instagramUrl"),
                "facebookUrl", application.get("facebookUrl"),
                "websiteUrl", application.get("websiteUrl")));
        response.put("contact", orderedMap(
                "contactName", application.get("contactName"),
                "contactPhone", application.get("contactPhone"),
                "contactEmail", application.get("contactEmail"),
                "address", joinAddress(
                        application.get("vendorCity"),
                        application.get("vendorDistrict"),
                        application.get("vendorAddress"))));
        response.put("applicationDetail", orderedMap(
                "applyDates", application.get("applyDates"),
                "applicationDateCount", application.get("applicationDateCount"),
                "selectedStallCount", application.get("selectedStallCount"),
                "vehicleNo", application.get("vehicleNo"),
                "applicantNote", application.get("applicantNote"),
                "reviewNote", application.get("reviewNote")));
        response.put("stalls", toVendorApplicationDateResponses(applicationDates));
        response.put("fee", orderedMap(
                "baseFee", application.get("baseFee"),
                "totalAmount", application.get("totalAmount"),
                "depositAmount", application.get("depositAmount"),
                "paymentDueAt", formatDateTimeValue(application.get("paymentDueAt")),
                "paymentNo", application.get("paymentNo"),
                "paymentAmount", application.get("paymentAmount"),
                "paymentProvider", application.get("paymentProvider"),
                "paymentProviderTradeNo", application.get("paymentProviderTradeNo"),
                "paymentRecordStatus", application.get("paymentRecordStatus"),
                "paidAt", formatDateTimeValue(application.get("paidAt")),
                "refundNo", application.get("refundNo"),
                "refundAmount", application.get("refundAmount"),
                "refundedAt", formatDateTimeValue(application.get("refundedAt"))));
        response.put("equipmentRentals", toVendorEquipmentRentalResponses(equipmentRentals));
        response.put("actions", orderedMap(
                "canManagePayment", canManagePayment(application),
                "canSelectStall", canSelectStall(application),
                "canRequestRefund", canRequestRefund(application)));
        return response;
    }

    /**
     * 將報名日期轉成前端選位區塊可直接使用的格式。
     */
    private List<Map<String, Object>> toVendorApplicationDateResponses(List<Map<String, Object>> applicationDates) {
        return applicationDates.stream()
                .map(date -> orderedMap(
                        "applicationDateId", date.get("applicationDateId"),
                        "applyDate", formatDateValue(date.get("applyDate")),
                        "selectedStallId", date.get("selectedStallId"),
                        "stallNo", date.get("stallNo"),
                        "zoneName", date.get("zoneName"),
                        "width", date.get("width"),
                        "length", date.get("length"),
                        "height", date.get("height"),
                        "selectionStatus", date.get("selectedStallId") == null ? "未選位" : "已選位"))
                .toList();
    }

    /**
     * 將設備租借資料依 rental id 合併，避免有多個電器時主設備重複出現。
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> toVendorEquipmentRentalResponses(List<Map<String, Object>> equipmentRentals) {
        Map<Object, Map<String, Object>> rentalsById = new LinkedHashMap<>();
        for (Map<String, Object> row : equipmentRentals) {
            Object rentalId = row.get("equipmentRentalId");
            Map<String, Object> rental = rentalsById.computeIfAbsent(rentalId, id -> orderedMap(
                    "equipmentRentalId", row.get("equipmentRentalId"),
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
                    "appliances", new java.util.ArrayList<Map<String, Object>>()));
            if (row.get("applianceId") != null) {
                List<Map<String, Object>> appliances = (List<Map<String, Object>>) rental.get("appliances");
                appliances.add(orderedMap(
                        "applianceId", row.get("applianceId"),
                        "applianceName", row.get("applianceName"),
                        "wattage", row.get("wattage")));
            }
        }
        return List.copyOf(rentalsById.values());
    }

    /**
     * 待付款、付款失敗或付款逾期時，前台可顯示「前往收款管理」。
     */
    private boolean canManagePayment(Map<String, Object> application) {
        return !isTrue(application.get("isCancelled"))
                && "APPROVED".equals(stringValue(application.get("reviewStatus")))
                && !"PAID".equals(stringValue(application.get("paymentStatus")))
                && stringValue(application.get("refundStatus")).isEmpty();
    }

    /**
     * 已付款但尚未完成全部活動日期選位時，前台可顯示「選擇攤位」。
     */
    private boolean canSelectStall(Map<String, Object> application) {
        return !isTrue(application.get("isCancelled"))
                && "APPROVED".equals(stringValue(application.get("reviewStatus")))
                && "PAID".equals(stringValue(application.get("paymentStatus")))
                && stringValue(application.get("refundStatus")).isEmpty()
                && toLong(application.get("selectedStallCount")) < toLong(application.get("applicationDateCount"));
    }

    /**
     * 已付款且尚未進入退款流程時，前台可顯示「退款」。
     */
    private boolean canRequestRefund(Map<String, Object> application) {
        return !isTrue(application.get("isCancelled"))
                && "PAID".equals(stringValue(application.get("paymentStatus")))
                && stringValue(application.get("refundStatus")).isEmpty();
    }

    private String formatEventDate(Object startValue, Object endValue) {
        LocalDate startDate = toLocalDate(startValue);
        LocalDate endDate = toLocalDate(endValue);
        if (startDate == null && endDate == null) {
            return null;
        }
        if (startDate == null) {
            return endDate.toString();
        }
        if (endDate == null || startDate.equals(endDate)) {
            return startDate.toString();
        }
        return startDate + " - " + endDate;
    }

    private String formatDateValue(Object value) {
        LocalDate date = toLocalDate(value);
        return date == null ? null : date.toString();
    }

    private String formatDateTimeValue(Object value) {
        LocalDateTime dateTime = toLocalDateTime(value);
        return dateTime == null ? null : dateTime.toString();
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
                "application", applicationData == null ? null
                        : orderedMap(
                                "id", detail.get("applicationId")),
                "vendor", applicationData == null ? null
                        : orderedMap(
                                "brandName", detail.get("brandName"),
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
                && toLong(applicationData.get("selectedStallCount"))
                        .equals(toLong(applicationData.get("applicationDateCount")))
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

    private Integer toInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String string && !string.isBlank()) {
            return Integer.valueOf(string);
        }
        return null;
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        if (value instanceof String string && !string.isBlank()) {
            return new BigDecimal(string);
        }
        return BigDecimal.ZERO;
    }

    private List<LocalDate> normalizedApplyDates(List<LocalDate> applyDates) {
        if (applyDates == null) {
            return List.of();
        }
        List<LocalDate> normalizedDates = new ArrayList<>(new LinkedHashSet<>(
                applyDates.stream()
                        .filter(date -> date != null)
                        .toList()));
        normalizedDates.sort(LocalDate::compareTo);
        return normalizedDates;
    }

    private String nextApplicationNo(Map<String, Object> event) {
        LocalDate eventStartDate = toLocalDate(event.get("startAt"));
        LocalDate prefixDate = eventStartDate == null ? LocalDate.now() : eventStartDate;
        String prefix = "MD" + prefixDate.format(DateTimeFormatter.BASIC_ISO_DATE);
        String latestApplicationNo = stallRepository.findLatestApplicationNoByPrefix(prefix).orElse(null);
        int nextSequence = 1;
        if (latestApplicationNo != null && latestApplicationNo.length() > prefix.length()) {
            String sequenceText = latestApplicationNo.substring(prefix.length());
            try {
                nextSequence = Integer.parseInt(sequenceText) + 1;
            } catch (NumberFormatException ignored) {
                nextSequence = 1;
            }
        }
        return prefix + String.format("%04d", nextSequence);
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private Map<String, Object> orderedMap(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            map.put(keyValues[i].toString(), keyValues[i + 1]);
        }
        return map;
    }

    private record PreparedEquipmentRental(
            Long eventEquipmentId,
            String equipmentName,
            BigDecimal rentalFee,
            String pricingUnit,
            String unit,
            Integer quantity,
            Integer rentalUnits,
            BigDecimal subtotal,
            List<VendorApplicationSubmitRequest.Appliance> appliances) {
    }

    /**
     * 查詢攤主專區可報名的市集；Repository 負責條件篩選，Service 負責 DTO 轉換與分頁。
     */
    public ApiResponse<MarketSearchResponse> searchMarkets(
            String keyword,
            String city,
            String district,
            String status,
            LocalDate eventStartAt,
            LocalDate eventEndAt,
            Integer page,
            Integer pageSize) {

        List<MarketSummaryResponse> markets = stallRepository
                .findMarkets(
                        keyword,
                        city,
                        district,
                        status,
                        eventStartAt,
                        eventEndAt)
                .stream()
                .map(MarketSummaryResponse::new)
                .toList();

        return ApiResponse.success(
                "Market list retrieved successfully",
                new MarketSearchResponse(
                        PageResponse.from(
                                markets,
                                page,
                                pageSize)));
    }

}
