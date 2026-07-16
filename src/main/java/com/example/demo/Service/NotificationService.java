package com.example.demo.Service;

import java.util.Collection;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.Repository.NotificationRepository;
import com.example.demo.dto.notification.NotificationCreateCommand;
import com.example.demo.enums.notification.NotificationCategory;
import com.example.demo.enums.notification.NotificationTargetType;
import com.example.demo.enums.notification.NotificationType;

@Service
public class NotificationService {

    private static final int MAX_TITLE_LENGTH = 150;

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    /**
     * Generic extension point for future APIs. Domain services should call this
     * method only after their business state has actually changed.
     */
    public void create(NotificationCreateCommand command) {
        validate(command);
        notificationRepository.create(command);
    }

    /**
     * Creates one notification row per recipient. This keeps read state isolated
     * while allowing announcements and event changes to fan out efficiently.
     */
    @Transactional
    public void createAll(Collection<NotificationCreateCommand> commands) {
        if (commands == null || commands.isEmpty()) {
            throw new IllegalArgumentException("Notification commands are required");
        }
        commands.forEach(this::validate);
        notificationRepository.createAll(commands);
    }

    public void notifySystemAnnouncement(Collection<Long> userIds, String title, String content) {
        createAll(commandsForUsers(
                userIds,
                NotificationCategory.SYSTEM,
                NotificationType.SYSTEM_ANNOUNCEMENT,
                NotificationTargetType.SYSTEM,
                null,
                title,
                content));
    }

    public void notifyEventChanged(
            Collection<Long> userIds,
            Long eventId,
            String title,
            String content) {
        createAll(commandsForUsers(
                userIds,
                NotificationCategory.EVENT_CHANGE,
                NotificationType.EVENT_UPDATED,
                NotificationTargetType.MARKET_EVENT,
                eventId,
                title,
                content));
    }

    public void notifyApplicationSubmitted(Long userId, Long applicationId, String eventTitle) {
        String eventName = eventName(eventTitle);
        create(new NotificationCreateCommand(
                userId,
                NotificationCategory.APPLICATION_REVIEW,
                NotificationType.APPLICATION_SUBMITTED,
                NotificationTargetType.EVENT_APPLICATION,
                applicationId,
                "待審核",
                eventName + " 已收到您的報名申請"));
    }

    public void notifyApplicationReviewed(
            Long userId,
            Long applicationId,
            String eventTitle,
            boolean approved) {
        String eventName = eventName(eventTitle);
        create(new NotificationCreateCommand(
                userId,
                NotificationCategory.APPLICATION_REVIEW,
                approved ? NotificationType.APPLICATION_APPROVED : NotificationType.APPLICATION_REJECTED,
                NotificationTargetType.EVENT_APPLICATION,
                applicationId,
                approved ? "待付款" : "審核未通過",
                approved
                        ? eventName + " 審核通過，請完成付款"
                        : eventName + " 報名審核未通過"));
    }

    public void notifyPaymentStatusChanged(
            Long userId,
            Long applicationId,
            String eventTitle,
            boolean paid) {
        String eventName = eventName(eventTitle);
        create(new NotificationCreateCommand(
                userId,
                NotificationCategory.PAYMENT,
                paid ? NotificationType.PAYMENT_PAID : NotificationType.PAYMENT_FAILED,
                NotificationTargetType.EVENT_APPLICATION,
                applicationId,
                paid ? "付款成功" : "付款失敗",
                paid
                        ? eventName + " 付款成功，可於開放選位後選擇攤位"
                        : eventName + " 付款失敗，請重新確認付款狀態"));
        if (paid) {
            create(new NotificationCreateCommand(
                    userId,
                    NotificationCategory.STALL_ASSIGNMENT,
                    NotificationType.STALL_SELECTION_AVAILABLE,
                    NotificationTargetType.EVENT_APPLICATION,
                    applicationId,
                    "待選位",
                    eventName + " 付款成功，可選擇攤位"));
        }
    }

    public void notifyStallSelectionCompleted(Long userId, Long applicationId, String eventTitle) {
        create(new NotificationCreateCommand(
                userId,
                NotificationCategory.STALL_ASSIGNMENT,
                NotificationType.APPLICATION_COMPLETED,
                NotificationTargetType.EVENT_APPLICATION,
                applicationId,
                "報名完成",
                eventName(eventTitle) + " 已完成選位"));
    }

    private void validate(NotificationCreateCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Notification command is required");
        }
        if (command.userId() == null || command.userId() <= 0) {
            throw new IllegalArgumentException("Notification user id is required");
        }
        if (command.category() == null || command.type() == null || command.targetType() == null) {
            throw new IllegalArgumentException("Notification classification is required");
        }
        if (command.targetType() == NotificationTargetType.SYSTEM) {
            if (command.targetId() != null) {
                throw new IllegalArgumentException("System notification must not have a target id");
            }
        } else if (command.targetId() == null || command.targetId() <= 0) {
            throw new IllegalArgumentException("Notification target id is required");
        }
        if (isBlank(command.title()) || command.title().length() > MAX_TITLE_LENGTH) {
            throw new IllegalArgumentException("Notification title is invalid");
        }
        if (isBlank(command.content())) {
            throw new IllegalArgumentException("Notification content is required");
        }
    }

    private String eventName(String eventTitle) {
        return isBlank(eventTitle) ? "活動" : eventTitle.trim();
    }

    private List<NotificationCreateCommand> commandsForUsers(
            Collection<Long> userIds,
            NotificationCategory category,
            NotificationType type,
            NotificationTargetType targetType,
            Long targetId,
            String title,
            String content) {
        if (userIds == null || userIds.isEmpty()) {
            throw new IllegalArgumentException("Notification recipients are required");
        }
        return userIds.stream()
                .distinct()
                .map(userId -> new NotificationCreateCommand(
                        userId,
                        category,
                        type,
                        targetType,
                        targetId,
                        title,
                        content))
                .toList();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
