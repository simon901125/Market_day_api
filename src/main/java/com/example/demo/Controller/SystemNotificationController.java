package com.example.demo.Controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.Service.NotificationService;
import com.example.demo.dto.request.SystemAnnouncementRequest;
import com.example.demo.dto.response.ApiResponse;
import com.example.demo.dto.response.MapBackedResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@Tag(name = "系統通知 API", description = "提供全員系統公告發送功能")
public class SystemNotificationController {

    private final NotificationService notificationService;

    public SystemNotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Operation(summary = "發送全員系統公告", description = "僅管理員可使用；通知所有 ACTIVE 的管理員、主辦方與攤主帳號。")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/api/admin/notifications/announcements")
    public ResponseEntity<ApiResponse<MapBackedResponse>> broadcastSystemAnnouncement(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @Valid @RequestBody SystemAnnouncementRequest request) {
        ApiResponse<MapBackedResponse> response = notificationService.broadcastSystemAnnouncement(
                authorizationHeader,
                request);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }
}
