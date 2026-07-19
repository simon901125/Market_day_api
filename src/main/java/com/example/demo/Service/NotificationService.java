package com.example.demo.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.Repository.NotificationRepo;
import com.example.demo.Repository.NotificationRepository;
import com.example.demo.Repository.UserRepo;
import com.example.demo.dto.notification.NotificationCreateCommand;
import com.example.demo.dto.request.SystemAnnouncementRequest;
import com.example.demo.dto.response.ApiResponse;
import com.example.demo.dto.response.MapBackedResponse;
import com.example.demo.dto.response.NotificationToggleDto;
import com.example.demo.entity.Notification;
import com.example.demo.entity.User;
import com.example.demo.enums.notification.NotificationCategory;
import com.example.demo.enums.notification.NotificationTargetType;
import com.example.demo.enums.notification.NotificationType;
import com.example.demo.enums.status.UserStatus;
import com.example.demo.enums.type.Role;

@Service
public class NotificationService {

    private static final int MAX_TITLE_LENGTH = 150;

    private final NotificationRepository notificationRepository;
    private final NotificationRepo notificationRepo;
    private final UserRepo userRepo;
    private final JwtService jwtService;

    public NotificationService(
            NotificationRepository notificationRepository,
            NotificationRepo notificationRepo,
            UserRepo userRepo,
            JwtService jwtService) {
        this.notificationRepository = notificationRepository;
        this.notificationRepo = notificationRepo;
        this.userRepo = userRepo;
        this.jwtService = jwtService;
    }

    public ApiResponse<NotificationToggleDto> markAsRead(String authorizationHeader, Long id) {
        Map<String, Object> auth = authenticatedUser(authorizationHeader);
        if (auth.containsKey("message")) {
            return ApiResponse.fail(auth.get("message").toString());
        }
        Long userId = ((Number) auth.get("userId")).longValue();

        Notification notification = notificationRepo.findById(id).orElse(null);
        if (notification == null) {
            return ApiResponse.fail("Notification not found");
        }
        if (!notification.getUser().getId().equals(userId)) {
            return ApiResponse.fail("Notification does not belong to this account");
        }

        if (!Boolean.TRUE.equals(notification.getIsRead())) {
            notification.setIsRead(true);
            notification.setReadAt(LocalDateTime.now());
            notificationRepo.save(notification);
        }

        return ApiResponse.success(
                "Notification marked as read",
                new NotificationToggleDto(notification.getId(), notification.getIsRead()));
    }

    private Map<String, Object> authenticatedUser(String authorizationHeader) {
        String token = jwtService.extractTokenFromAuthorizationHeader(authorizationHeader);
        if (token == null || token.isBlank()) {
            return Map.of("message", "Authorization token is required");
        }
        if (!jwtService.isTokenValid(token)) {
            return Map.of("message", "Invalid or expired token");
        }
        User user = userRepo.findByEmail(jwtService.getEmail(token)).orElse(null);
        if (user == null) {
            return Map.of("message", "User not found");
        }
        Map<String, Object> auth = new HashMap<>();
        auth.put("userId", user.getId());
        if (user.getRole() != null) {
            auth.put("role", user.getRole());
        }
        return auth;
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

    @Transactional
    public ApiResponse<MapBackedResponse> broadcastSystemAnnouncement(
            String authorizationHeader,
            SystemAnnouncementRequest request) {
        Map<String, Object> auth = authenticatedUser(authorizationHeader);
        if (auth.containsKey("message")) {
            return ApiResponse.fail(401, auth.get("message").toString());
        }
        if (auth.get("role") != Role.ADMIN) {
            return ApiResponse.fail(403, "Only administrators can broadcast system announcements");
        }
        if (request == null || isBlank(request.title()) || isBlank(request.content())) {
            return ApiResponse.fail(400, "公告標題與內容不可為空");
        }
        if (request.title().trim().length() > MAX_TITLE_LENGTH) {
            return ApiResponse.fail(400, "公告標題不可超過 150 字");
        }

        List<Long> recipientIds = userRepo.findIdsByStatus(UserStatus.ACTIVE);
        if (recipientIds.isEmpty()) {
            return ApiResponse.fail(409, "目前沒有可接收公告的啟用帳號");
        }
        String title = request.title().trim();
        String content = request.content().trim();
        notifySystemAnnouncement(recipientIds, title, content);

        return ApiResponse.success(
                "系統公告已發送",
                new MapBackedResponse(Map.of(
                        "recipientCount", recipientIds.size(),
                        "announcementKey", fingerprint(title, content))));
    }

    public void notifyApplicationCancelled(
            Long vendorUserId,
            Long organizerUserId,
            Long applicationId,
            String eventTitle,
            String brandName) {
        String eventName = eventName(eventTitle);
        create(new NotificationCreateCommand(
                vendorUserId,
                NotificationCategory.APPLICATION_REVIEW,
                NotificationType.APPLICATION_CANCELLED,
                NotificationTargetType.EVENT_APPLICATION,
                applicationId,
                "報名已取消",
                eventName + " 的活動報名已取消",
                dedupKey(vendorUserId, NotificationType.APPLICATION_CANCELLED,
                        NotificationTargetType.EVENT_APPLICATION, applicationId, null)));
        create(new NotificationCreateCommand(
                organizerUserId,
                NotificationCategory.REGISTRATION,
                NotificationType.APPLICATION_CANCELLED,
                NotificationTargetType.EVENT_APPLICATION,
                applicationId,
                "攤主取消報名",
                brandName(brandName) + "已取消活動報名：" + eventName,
                dedupKey(organizerUserId, NotificationType.APPLICATION_CANCELLED,
                        NotificationTargetType.EVENT_APPLICATION, applicationId, null)));
    }

    public void notifyVendorRefundRequested(Long vendorUserId, Long refundId, String eventTitle) {
        create(new NotificationCreateCommand(
                vendorUserId,
                NotificationCategory.PAYMENT,
                NotificationType.REFUND_REQUESTED,
                NotificationTargetType.REFUND,
                refundId,
                "退款申請已送出",
                eventName(eventTitle) + " 的退款申請已送出，等待後續處理",
                dedupKey(vendorUserId, NotificationType.REFUND_REQUESTED,
                        NotificationTargetType.REFUND, refundId, null)));
    }

    public void notifyDepositReturned(
            Long vendorUserId,
            Long applicationId,
            String eventTitle) {
        create(new NotificationCreateCommand(
                vendorUserId,
                NotificationCategory.PAYMENT,
                NotificationType.DEPOSIT_RETURNED,
                NotificationTargetType.EVENT_APPLICATION,
                applicationId,
                "保證金已退還",
                eventName(eventTitle) + " 的保證金已由主辦方登記為現金退還",
                dedupKey(vendorUserId, NotificationType.DEPOSIT_RETURNED,
                        NotificationTargetType.EVENT_APPLICATION, applicationId, null)));
    }

    public void notifyPasswordResetCompleted(
            Long resetUserId,
            String accountEmail,
            String resetEventKey) {
        List<Long> recipientIds = new ArrayList<>(
                userRepo.findIdsByRoleAndStatus(Role.ADMIN, UserStatus.ACTIVE));
        recipientIds.add(resetUserId);
        String content = "帳號 " + accountEmail + " 已完成密碼重設";
        List<NotificationCreateCommand> commands = recipientIds.stream()
                .distinct()
                .sorted()
                .map(recipientId -> new NotificationCreateCommand(
                        recipientId,
                        NotificationCategory.SYSTEM,
                        NotificationType.PASSWORD_RESET_COMPLETED,
                        NotificationTargetType.USER,
                        resetUserId,
                        "密碼重設完成",
                        content,
                        dedupKey(recipientId, NotificationType.PASSWORD_RESET_COMPLETED,
                                NotificationTargetType.USER, resetUserId, resetEventKey)))
                .toList();
        createAll(commands);
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
                eventName + " 已收到您的報名申請",
                dedupKey(userId, NotificationType.APPLICATION_SUBMITTED,
                        NotificationTargetType.EVENT_APPLICATION, applicationId, null)));
    }

    public void notifyOrganizerApplicationSubmitted(
            Long organizerUserId,
            Long applicationId,
            String eventTitle,
            String brandName) {
        create(new NotificationCreateCommand(
                organizerUserId,
                NotificationCategory.REGISTRATION,
                NotificationType.APPLICATION_SUBMITTED,
                NotificationTargetType.EVENT_APPLICATION,
                applicationId,
                "新報名",
                brandName(brandName) + "送出報名申請：" + eventName(eventTitle),
                dedupKey(organizerUserId, NotificationType.APPLICATION_SUBMITTED,
                        NotificationTargetType.EVENT_APPLICATION, applicationId, null)));
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
                        : eventName + " 報名審核未通過",
                dedupKey(userId,
                        approved ? NotificationType.APPLICATION_APPROVED : NotificationType.APPLICATION_REJECTED,
                        NotificationTargetType.EVENT_APPLICATION, applicationId, null)));
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
                        : eventName + " 付款失敗，請重新確認付款狀態",
                dedupKey(userId, paid ? NotificationType.PAYMENT_PAID : NotificationType.PAYMENT_FAILED,
                        NotificationTargetType.EVENT_APPLICATION, applicationId, null)));
        if (paid) {
            create(new NotificationCreateCommand(
                    userId,
                    NotificationCategory.STALL_ASSIGNMENT,
                    NotificationType.STALL_SELECTION_AVAILABLE,
                    NotificationTargetType.EVENT_APPLICATION,
                    applicationId,
                    "待選位",
                    eventName + " 付款成功，可選擇攤位",
                    dedupKey(userId, NotificationType.STALL_SELECTION_AVAILABLE,
                            NotificationTargetType.EVENT_APPLICATION, applicationId, null)));
        }
    }

    public void notifyOrganizerPaymentStatusChanged(
            Long organizerUserId,
            Long applicationId,
            String eventTitle,
            String brandName,
            boolean paid) {
        create(new NotificationCreateCommand(
                organizerUserId,
                NotificationCategory.PAYMENT,
                paid ? NotificationType.PAYMENT_PAID : NotificationType.PAYMENT_FAILED,
                NotificationTargetType.EVENT_APPLICATION,
                applicationId,
                paid ? "付款完成" : "付款失敗",
                brandName(brandName)
                        + (paid ? "已完成付款：" : "付款失敗：")
                        + eventName(eventTitle),
                dedupKey(organizerUserId,
                        paid ? NotificationType.PAYMENT_PAID : NotificationType.PAYMENT_FAILED,
                        NotificationTargetType.EVENT_APPLICATION, applicationId, null)));
    }

    public void notifyStallSelectionCompleted(Long userId, Long applicationId, String eventTitle) {
        create(new NotificationCreateCommand(
                userId,
                NotificationCategory.STALL_ASSIGNMENT,
                NotificationType.APPLICATION_COMPLETED,
                NotificationTargetType.EVENT_APPLICATION,
                applicationId,
                "報名完成",
                eventName(eventTitle) + " 已完成選位",
                dedupKey(userId, NotificationType.APPLICATION_COMPLETED,
                        NotificationTargetType.EVENT_APPLICATION, applicationId, null)));
    }

    public void notifyOrganizerStallSelectionCompleted(
            Long organizerUserId,
            Long applicationId,
            String eventTitle,
            String brandName) {
        create(new NotificationCreateCommand(
                organizerUserId,
                NotificationCategory.STALL_ASSIGNMENT,
                NotificationType.STALL_SELECTION_COMPLETED,
                NotificationTargetType.EVENT_APPLICATION,
                applicationId,
                "完成選位",
                brandName(brandName) + "已完成攤位選擇：" + eventName(eventTitle),
                dedupKey(organizerUserId, NotificationType.STALL_SELECTION_COMPLETED,
                        NotificationTargetType.EVENT_APPLICATION, applicationId, null)));
    }

    public void notifyRefundRequested(Long userId, Long refundId, String eventTitle) {
        create(new NotificationCreateCommand(
                userId,
                NotificationCategory.PAYMENT,
                NotificationType.REFUND_REQUESTED,
                NotificationTargetType.REFUND,
                refundId,
                "退款申請待審核",
                eventName(eventTitle) + " 已收到攤主退款申請，請進行審核。",
                dedupKey(userId, NotificationType.REFUND_REQUESTED,
                        NotificationTargetType.REFUND, refundId, null)));
    }

    public void notifyRefundProcessingToVendor(Long userId, Long refundId, String eventTitle) {
        create(new NotificationCreateCommand(
                userId,
                NotificationCategory.PAYMENT,
                NotificationType.REFUNDING,
                NotificationTargetType.REFUND,
                refundId,
                "退款處理中",
                eventName(eventTitle) + " 的退款正在處理中",
                dedupKey(userId, NotificationType.REFUNDING,
                        NotificationTargetType.REFUND, refundId, null)));
    }


    public void notifyRefundSucceededToVendor(Long userId, Long refundId, String eventTitle) {
        create(new NotificationCreateCommand(
                userId,
                NotificationCategory.PAYMENT,
                NotificationType.REFUNDED,
                NotificationTargetType.REFUND,
                refundId,
                "退款完成",
                eventName(eventTitle) + " 的退款已完成",
                dedupKey(userId, NotificationType.REFUNDED,
                        NotificationTargetType.REFUND, refundId, null)));
    }

    public void notifyRefundSucceededToOrganizer(Long organizerUserId, Long refundId, String eventTitle) {
        create(new NotificationCreateCommand(
                organizerUserId,
                NotificationCategory.PAYMENT,
                NotificationType.REFUNDED,
                NotificationTargetType.REFUND,
                refundId,
                "退款完成",
                eventName(eventTitle) + " 的退款已完成",
                dedupKey(organizerUserId, NotificationType.REFUNDED,
                        NotificationTargetType.REFUND, refundId, null)));
    }

    public void notifyRefundFailedToVendor(Long userId, Long refundId, String eventTitle) {
        create(new NotificationCreateCommand(
                userId,
                NotificationCategory.PAYMENT,
                NotificationType.REFUND_FAILED,
                NotificationTargetType.REFUND,
                refundId,
                "退款失敗",
                eventName(eventTitle) + " 的退款處理失敗，請等待主辦方重新處理",
                dedupKey(userId, NotificationType.REFUND_FAILED,
                        NotificationTargetType.REFUND, refundId, null)));
    }

    public void notifyRefundFailedToOrganizer(Long organizerUserId, Long refundId, String eventTitle) {
        create(new NotificationCreateCommand(
                organizerUserId,
                NotificationCategory.PAYMENT,
                NotificationType.REFUND_FAILED,
                NotificationTargetType.REFUND,
                refundId,
                "退款失敗",
                eventName(eventTitle) + " 的退款失敗，請重試或確認藍新金流狀態",
                dedupKey(organizerUserId, NotificationType.REFUND_FAILED,
                        NotificationTargetType.REFUND, refundId, null)));
    }

    public void notifyAdminsEventSubmitted(Long eventId, String eventTitle, boolean resubmitted) {
        NotificationType type = resubmitted
                ? NotificationType.EVENT_RESUBMITTED
                : NotificationType.EVENT_SUBMITTED;
        String title = resubmitted ? "活動補件重新送審" : "活動送審";
        String content = eventName(eventTitle) + (resubmitted ? " 已完成補件並重新送審" : " 已送出審核");
        List<Long> adminUserIds = userRepo.findIdsByRoleAndStatus(Role.ADMIN, UserStatus.ACTIVE);
        createAll(adminUserIds.stream()
                .distinct()
                .sorted()
                .map(userId -> new NotificationCreateCommand(
                        userId,
                        NotificationCategory.EVENT_MANAGEMENT,
                        type,
                        NotificationTargetType.MARKET_EVENT,
                        eventId,
                        title,
                        content,
                        dedupKey(userId, type, NotificationTargetType.MARKET_EVENT, eventId, null)))
                .toList());
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
        if (command.dedupKey() != null
                && (command.dedupKey().isBlank() || command.dedupKey().length() > 255)) {
            throw new IllegalArgumentException("Notification dedup key is invalid");
        }
    }

    private String eventName(String eventTitle) {
        return isBlank(eventTitle) ? "活動" : eventTitle.trim();
    }

    private String brandName(String brandName) {
        return isBlank(brandName) ? "攤主" : "品牌「" + brandName.trim() + "」";
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
                .sorted()
                .map(userId -> new NotificationCreateCommand(
                        userId,
                        category,
                        type,
                        targetType,
                        targetId,
                        title,
                        content,
                        dedupKey(userId, type, targetType, targetId, fingerprint(title, content))))
                .toList();
    }

    private String dedupKey(
            Long userId,
            NotificationType type,
            NotificationTargetType targetType,
            Long targetId,
            String version) {
        String target = targetId == null ? "none" : targetId.toString();
        String suffix = isBlank(version) ? "v1" : version;
        return userId + ":" + type.name() + ":" + targetType.name() + ":" + target + ":" + suffix;
    }

    private String fingerprint(String title, String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((String.valueOf(title) + "\n" + String.valueOf(content))
                    .getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
