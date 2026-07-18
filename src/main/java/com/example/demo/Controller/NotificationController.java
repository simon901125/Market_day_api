package com.example.demo.Controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.Service.NotificationService;
import com.example.demo.dto.response.ApiResponse;
import com.example.demo.dto.response.NotificationToggleDto;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name = "通知 API", description = "提供登入使用者操作自己的通知")
public class NotificationController {

    @Autowired
    private NotificationService notificationService;

    @Operation(summary = "標記通知為已讀", description = "將指定通知標記為已讀，僅限通知擁有者操作")
    @PostMapping("/api/notification/{id}/isRead")
    public ApiResponse<NotificationToggleDto> markNotificationAsRead(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable Long id) {
        return notificationService.markAsRead(authorizationHeader, id);
    }
}
