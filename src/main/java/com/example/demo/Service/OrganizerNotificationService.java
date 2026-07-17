package com.example.demo.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.example.demo.Repository.NotificationRepository;
import com.example.demo.Repository.OrganizerRepository;
import com.example.demo.dto.response.ApiResponse;
import com.example.demo.dto.response.OrganizerNotificationItemResponse;
import com.example.demo.dto.response.OrganizerNotificationSearchResponse;
import com.example.demo.dto.response.PageResponse;
import com.example.demo.enums.notification.OrganizerNotificationFilter;

@Service
public class OrganizerNotificationService {

    private final NotificationRepository notificationRepository;
    private final OrganizerRepository organizerRepository;
    private final JwtService jwtService;
    private final int retentionYears;

    public OrganizerNotificationService(
            NotificationRepository notificationRepository,
            OrganizerRepository organizerRepository,
            JwtService jwtService,
            @Value("${notification.retention-years:1}") int retentionYears) {
        this.notificationRepository = notificationRepository;
        this.organizerRepository = organizerRepository;
        this.jwtService = jwtService;
        this.retentionYears = Math.max(retentionYears, 1);
    }

    public ApiResponse<OrganizerNotificationSearchResponse> getNotifications(
            String authorizationHeader,
            String rawFilter,
            Integer page,
            Integer pageSize) {
        Map<String, Object> organizer = authenticatedOrganizer(authorizationHeader);
        if (organizer.containsKey("message")) {
            return ApiResponse.fail(organizer.get("message").toString());
        }

        OrganizerNotificationFilter filter;
        try {
            filter = OrganizerNotificationFilter.from(rawFilter);
        } catch (IllegalArgumentException exception) {
            return ApiResponse.fail(exception.getMessage());
        }

        int normalizedPage = PageResponse.normalizePage(page);
        int normalizedPageSize = PageResponse.normalizePageSize(pageSize);
        int offset = (normalizedPage - 1) * normalizedPageSize;
        Long userId = ((Number) organizer.get("userId")).longValue();
        LocalDateTime retentionStart = LocalDateTime.now().minusYears(retentionYears);

        long totalItems = notificationRepository.countOrganizerNotifications(
                userId, filter.categories(), filter.unreadOnly(), retentionStart);
        long unreadCount = notificationRepository.countOrganizerNotifications(
                userId, java.util.Set.of(), true, retentionStart);
        List<OrganizerNotificationItemResponse> items = notificationRepository.findOrganizerNotifications(
                userId,
                filter.categories(),
                filter.unreadOnly(),
                retentionStart,
                offset,
                normalizedPageSize);

        return ApiResponse.success(
                "Organizer notifications retrieved successfully",
                new OrganizerNotificationSearchResponse(
                        unreadCount,
                        new PageResponse<>(items, normalizedPage, normalizedPageSize, totalItems)));
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

        Map<String, Object> organizer = organizerRepository
                .findOrganizerAccountByEmail(jwtService.getEmail(token))
                .orElse(null);
        if (organizer == null) {
            return Map.of("message", "Organizer profile not found");
        }
        if (!"ORGANIZER".equals(organizer.get("role"))) {
            return Map.of("message", "This account is not an organizer");
        }
        return organizer;
    }
}
