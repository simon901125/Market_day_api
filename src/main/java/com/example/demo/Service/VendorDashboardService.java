package com.example.demo.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.example.demo.Repository.NotificationRepository;
import com.example.demo.Repository.StallRepository;
import com.example.demo.dto.response.ApiResponse;
import com.example.demo.dto.response.VendorDashboardInitResponse;
import com.example.demo.dto.response.VendorNotificationItemResponse;

@Service
public class VendorDashboardService {

    private static final int DASHBOARD_NOTIFICATION_LIMIT = 6;
    private static final String PROFILE_GUIDE_MESSAGE = "請先完成攤位必填資料";

    private final StallRepository stallRepository;
    private final NotificationRepository notificationRepository;
    private final JwtService jwtService;
    private final int retentionYears;

    public VendorDashboardService(
            StallRepository stallRepository,
            NotificationRepository notificationRepository,
            JwtService jwtService,
            @Value("${notification.retention-years:1}") int retentionYears) {
        this.stallRepository = stallRepository;
        this.notificationRepository = notificationRepository;
        this.jwtService = jwtService;
        this.retentionYears = Math.max(retentionYears, 1);
    }

    public ApiResponse<VendorDashboardInitResponse> initDashboard(String authorizationHeader) {
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

        Map<String, Object> vendor = stallRepository
                .findVendorDashboardProfileByEmail(jwtService.getEmail(token))
                .orElse(null);
        if (vendor == null) {
            return ApiResponse.fail("Vendor account not found");
        }
        if (!"VENDOR".equals(vendor.get("role"))) {
            return ApiResponse.fail("This account is not a vendor");
        }

        Long userId = toLong(vendor.get("userId"));
        Long vendorProfileId = nullableLong(vendor.get("vendorProfileId"));
        List<Map<String, Object>> products = vendorProfileId == null
                ? List.of()
                : stallRepository.findVendorProducts(vendorProfileId);

        if (isProfileIncomplete(vendor, products)) {
            return ApiResponse.success(
                    "攤主首次登入導引",
                    new VendorDashboardInitResponse(
                            true,
                            PROFILE_GUIDE_MESSAGE,
                            null,
                            0,
                            0,
                            0,
                            List.of()));
        }

        Map<String, Object> counts = stallRepository.findVendorDashboardApplicationCounts(userId);
        LocalDateTime retentionStart = LocalDateTime.now().minusYears(retentionYears);
        List<VendorNotificationItemResponse> notifications = notificationRepository.findVendorNotifications(
                userId,
                null,
                false,
                retentionStart,
                0,
                DASHBOARD_NOTIFICATION_LIMIT);

        return ApiResponse.success(
                "攤主首頁資料取得成功",
                new VendorDashboardInitResponse(
                        false,
                        null,
                        text(vendor.get("contactName")),
                        toLong(counts.get("pendingReviewCount")),
                        toLong(counts.get("pendingPaymentCount")),
                        toLong(counts.get("pendingStallSelectionCount")),
                        notifications));
    }

    private boolean isProfileIncomplete(
            Map<String, Object> vendor,
            List<Map<String, Object>> products) {
        if (isMissing(vendor.get("userProfileId"))
                || isMissing(vendor.get("vendorProfileId"))
                || isMissing(vendor.get("name"))
                || isMissing(vendor.get("contactName"))
                || isMissing(vendor.get("contactPhone"))
                || isMissing(vendor.get("contactEmail"))
                || isMissing(vendor.get("city"))
                || isMissing(vendor.get("district"))
                || isMissing(vendor.get("address"))
                || isMissing(vendor.get("categoryId"))
                || isMissing(vendor.get("avatarImageUrl"))
                || isMissing(vendor.get("coverImageUrl"))
                || isMissing(vendor.get("brandSummary"))
                || isMissing(vendor.get("brandDescription"))) {
            return true;
        }

        return products == null
                || products.isEmpty()
                || products.stream().anyMatch(product ->
                        isMissing(product.get("productName"))
                                || isMissing(product.get("productSummary"))
                                || isMissing(product.get("productPrice")));
    }

    private boolean isMissing(Object value) {
        return value == null || value instanceof String string && string.isBlank();
    }

    private String text(Object value) {
        return value == null ? null : value.toString();
    }

    private Long nullableLong(Object value) {
        if (value == null) {
            return null;
        }
        return toLong(value);
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
}
