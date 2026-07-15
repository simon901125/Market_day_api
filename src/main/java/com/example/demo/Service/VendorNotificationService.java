package com.example.demo.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.example.demo.Repository.NotificationRepository;
import com.example.demo.Repository.StallRepository;
import com.example.demo.dto.response.ApiResponse;
import com.example.demo.dto.response.PageResponse;
import com.example.demo.dto.response.VendorNotificationItemResponse;
import com.example.demo.dto.response.VendorNotificationSearchResponse;
import com.example.demo.enums.notification.VendorNotificationFilter;

@Service
public class VendorNotificationService {

    private final NotificationRepository notificationRepository;
    private final StallRepository stallRepository;
    private final JwtService jwtService;
    private final int retentionYears;

    public VendorNotificationService(
            NotificationRepository notificationRepository,
            StallRepository stallRepository,
            JwtService jwtService,
            @Value("${notification.retention-years:1}") int retentionYears) {
        this.notificationRepository = notificationRepository;
        this.stallRepository = stallRepository;
        this.jwtService = jwtService;
        this.retentionYears = Math.max(retentionYears, 1);
    }

    public ApiResponse<VendorNotificationSearchResponse> getNotifications(
            String authorizationHeader,
            String rawFilter,
            Integer page,
            Integer pageSize) {
        Map<String, Object> vendor = authenticatedVendor(authorizationHeader);
        if (vendor.containsKey("message")) {
            return ApiResponse.fail(vendor.get("message").toString());
        }

        VendorNotificationFilter filter;
        try {
            filter = VendorNotificationFilter.from(rawFilter);
        } catch (IllegalArgumentException exception) {
            return ApiResponse.fail(exception.getMessage());
        }

        int normalizedPage = PageResponse.normalizePage(page);
        int normalizedPageSize = PageResponse.normalizePageSize(pageSize);
        int offset = (normalizedPage - 1) * normalizedPageSize;
        Long userId = ((Number) vendor.get("userId")).longValue();
        LocalDateTime retentionStart = LocalDateTime.now().minusYears(retentionYears);

        long totalItems = notificationRepository.countVendorNotifications(
                userId, filter.category(), filter.unreadOnly(), retentionStart);
        long unreadCount = notificationRepository.countVendorNotifications(
                userId, null, true, retentionStart);
        List<VendorNotificationItemResponse> items = notificationRepository.findVendorNotifications(
                userId,
                filter.category(),
                filter.unreadOnly(),
                retentionStart,
                offset,
                normalizedPageSize);

        return ApiResponse.success(
                "Vendor notifications retrieved successfully",
                new VendorNotificationSearchResponse(
                        unreadCount,
                        new PageResponse<>(items, normalizedPage, normalizedPageSize, totalItems)));
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
}
