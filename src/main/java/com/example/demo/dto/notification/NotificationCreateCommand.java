package com.example.demo.dto.notification;

import com.example.demo.enums.notification.NotificationCategory;
import com.example.demo.enums.notification.NotificationTargetType;
import com.example.demo.enums.notification.NotificationType;

public record NotificationCreateCommand(
        Long userId,
        NotificationCategory category,
        NotificationType type,
        NotificationTargetType targetType,
        Long targetId,
        String title,
        String content) {
}
