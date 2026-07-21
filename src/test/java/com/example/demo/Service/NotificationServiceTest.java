package com.example.demo.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.demo.Repository.NotificationRepo;
import com.example.demo.Repository.NotificationRepository;
import com.example.demo.Repository.UserRepo;
import com.example.demo.dto.notification.NotificationCreateCommand;
import com.example.demo.dto.request.SystemAnnouncementRequest;
import com.example.demo.dto.response.ApiResponse;
import com.example.demo.entity.Notification;
import com.example.demo.entity.User;
import com.example.demo.enums.notification.NotificationCategory;
import com.example.demo.enums.notification.NotificationTargetType;
import com.example.demo.enums.notification.NotificationType;
import com.example.demo.enums.status.UserStatus;
import com.example.demo.enums.type.Role;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationRepo notificationRepo;

    @Mock
    private UserRepo userRepo;

    @Mock
    private JwtService jwtService;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(notificationRepository, notificationRepo, userRepo, jwtService);
    }

    @Test
    void genericCreatePersistsValidatedCommand() {
        NotificationCreateCommand command = new NotificationCreateCommand(
                10L,
                NotificationCategory.EVENT_CHANGE,
                NotificationType.EVENT_UPDATED,
                NotificationTargetType.MARKET_EVENT,
                20L,
                "活動異動",
                "活動時間已更新");

        notificationService.create(command);

        ArgumentCaptor<NotificationCreateCommand> captor = ArgumentCaptor.forClass(NotificationCreateCommand.class);
        verify(notificationRepository).create(captor.capture());
        assertThat(captor.getValue().targetType()).isEqualTo(NotificationTargetType.MARKET_EVENT);
        assertThat(captor.getValue().targetId()).isEqualTo(20L);
        assertThat(captor.getValue().content()).doesNotContain("20");
    }

    @Test
    void applicationSubmittedUsesApplicationReviewClassification() {
        notificationService.notifyApplicationSubmitted(10L, 20L, "夏日市集");

        NotificationCreateCommand command = capturedCommand();
        assertThat(command.category()).isEqualTo(NotificationCategory.APPLICATION_REVIEW);
        assertThat(command.type()).isEqualTo(NotificationType.APPLICATION_SUBMITTED);
        assertThat(command.targetType()).isEqualTo(NotificationTargetType.EVENT_APPLICATION);
        assertThat(command.targetId()).isEqualTo(20L);
        assertThat(command.dedupKey()).isEqualTo(
                "10:APPLICATION_SUBMITTED:EVENT_APPLICATION:20:v1");
        assertThat(command.content()).doesNotContain("20");
        assertThat(command.content()).contains("夏日市集");
    }

    @Test
    void reviewedPaymentAndStallHelpersUseExpectedEventTypes() {
        notificationService.notifyApplicationReviewed(10L, 20L, "夏日市集", false);
        notificationService.notifyPaymentStatusChanged(10L, 20L, "夏日市集", true);
        notificationService.notifyStallSelectionCompleted(10L, 20L, "夏日市集");

        ArgumentCaptor<NotificationCreateCommand> captor = ArgumentCaptor.forClass(NotificationCreateCommand.class);
        verify(notificationRepository, times(4)).create(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(NotificationCreateCommand::type)
                .containsExactly(
                        NotificationType.APPLICATION_REJECTED,
                        NotificationType.PAYMENT_PAID,
                        NotificationType.STALL_SELECTION_AVAILABLE,
                        NotificationType.APPLICATION_COMPLETED);
    }

    @Test
    void organizerHelpersCreateRecipientSpecificNotifications() {
        notificationService.notifyOrganizerApplicationSubmitted(30L, 20L, "夏日市集", "森日甜點");
        notificationService.notifyOrganizerPaymentStatusChanged(30L, 20L, "夏日市集", "森日甜點", true);
        notificationService.notifyOrganizerStallSelectionCompleted(30L, 20L, "夏日市集", "森日甜點");

        ArgumentCaptor<NotificationCreateCommand> captor = ArgumentCaptor.forClass(NotificationCreateCommand.class);
        verify(notificationRepository, times(3)).create(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(NotificationCreateCommand::type)
                .containsExactly(
                        NotificationType.APPLICATION_SUBMITTED,
                        NotificationType.PAYMENT_PAID,
                        NotificationType.STALL_SELECTION_COMPLETED);
        assertThat(captor.getAllValues())
                .allSatisfy(command -> {
                    assertThat(command.userId()).isEqualTo(30L);
                    assertThat(command.targetId()).isEqualTo(20L);
                    assertThat(command.content()).contains("森日甜點", "夏日市集");
                });
    }

    @Test
    void organizerEventWithdrawalAndCancellationUseRequestedTypes() {
        notificationService.notifyOrganizerApplicationResubmitted(30L, 50L, "測試活動");
        notificationService.notifyOrganizerEventCancelled(30L, 50L, "測試活動");

        ArgumentCaptor<NotificationCreateCommand> captor = ArgumentCaptor.forClass(NotificationCreateCommand.class);
        verify(notificationRepository, times(2)).create(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(NotificationCreateCommand::type)
                .containsExactly(
                        NotificationType.APPLICATION_RESUBMITTED,
                        NotificationType.EVENT_CANCELLED);
        assertThat(captor.getAllValues()).allSatisfy(command -> {
            assertThat(command.userId()).isEqualTo(30L);
            assertThat(command.targetType()).isEqualTo(NotificationTargetType.MARKET_EVENT);
            assertThat(command.targetId()).isEqualTo(50L);
            assertThat(command.content()).contains("測試活動");
            assertThat(command.dedupKey()).isNotBlank();
        });
    }

    @Test
    void refundLifecycleNotificationsUseRecipientSpecificDedupKeys() {
        notificationService.notifyRefundProcessingToVendor(10L, 40L, "夏日市集");
        notificationService.notifyRefundSucceededToVendor(10L, 40L, "夏日市集");
        notificationService.notifyRefundSucceededToOrganizer(20L, 40L, "夏日市集");
        notificationService.notifyRefundFailedToVendor(10L, 41L, "夏日市集");
        notificationService.notifyRefundFailedToOrganizer(20L, 41L, "夏日市集");

        ArgumentCaptor<NotificationCreateCommand> captor = ArgumentCaptor.forClass(NotificationCreateCommand.class);
        verify(notificationRepository, times(5)).create(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(NotificationCreateCommand::type)
                .containsExactly(
                        NotificationType.REFUNDING,
                        NotificationType.REFUNDED,
                        NotificationType.REFUNDED,
                        NotificationType.REFUND_FAILED,
                        NotificationType.REFUND_FAILED);
        assertThat(captor.getAllValues()).allSatisfy(command -> assertThat(command.dedupKey()).isNotBlank());
    }

    @Test
    void eventResubmissionFansOutToActiveAdministrators() {
        when(userRepo.findIdsByRoleAndStatus(Role.ADMIN, UserStatus.ACTIVE))
                .thenReturn(List.of(30L, 20L));

        notificationService.notifyAdminsEventSubmitted(50L, "夏日市集", "森林手作坊", true);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<NotificationCreateCommand>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(notificationRepository).createAll(captor.capture());
        assertThat(captor.getValue())
                .extracting(NotificationCreateCommand::userId)
                .containsExactly(20L, 30L);
        assertThat(captor.getValue()).allSatisfy(command -> {
            assertThat(command.type()).isEqualTo(NotificationType.EVENT_RESUBMITTED);
            assertThat(command.targetId()).isEqualTo(50L);
            assertThat(command.dedupKey()).isNotBlank();
        });
    }

    @Test
    void blankEventTitleIsRejectedInsteadOfCreatingGenericNotification() {
        assertThatThrownBy(() -> notificationService.notifyPaymentStatusChanged(10L, 20L, " ", false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Event title");
        verify(notificationRepository, never()).create(any());
    }

    @Test
    void invalidGenericCommandIsRejectedBeforeRepositoryCall() {
        NotificationCreateCommand command = new NotificationCreateCommand(
                null,
                NotificationCategory.PAYMENT,
                NotificationType.PAYMENT_PAID,
                NotificationTargetType.EVENT_APPLICATION,
                20L,
                "付款成功",
                "內容");

        assertThatThrownBy(() -> notificationService.create(command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Notification user id is required");
    }

    @Test
    void repositoryFailureIsPropagatedToTheCallingApiTransaction() {
        when(notificationRepository.create(any(NotificationCreateCommand.class)))
                .thenThrow(new IllegalStateException("notification insert failed"));

        assertThatThrownBy(() -> notificationService.notifyApplicationSubmitted(10L, 20L, "夏日市集"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("notification insert failed");
    }

    @Test
    void systemAnnouncementCreatesOneTargetlessNotificationPerDistinctRecipient() {
        notificationService.notifySystemAnnouncement(
                List.of(10L, 20L, 10L),
                "系統公告",
                "系統將進行維護");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<NotificationCreateCommand>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(notificationRepository).createAll(captor.capture());

        assertThat(captor.getValue())
                .hasSize(2)
                .allSatisfy(command -> {
                    assertThat(command.category()).isEqualTo(NotificationCategory.SYSTEM);
                    assertThat(command.type()).isEqualTo(NotificationType.SYSTEM_ANNOUNCEMENT);
                    assertThat(command.targetType()).isEqualTo(NotificationTargetType.SYSTEM);
                    assertThat(command.targetId()).isNull();
                });
    }

    @Test
    void administratorCanBroadcastAnnouncementToEveryActiveAccount() {
        User admin = userWithId(1L);
        admin.setRole(Role.ADMIN);
        authenticate("token", "admin@example.com", admin);
        when(userRepo.findIdsByStatus(UserStatus.ACTIVE)).thenReturn(List.of(1L, 2L, 3L));

        var response = notificationService.broadcastSystemAnnouncement(
                "Bearer token", new SystemAnnouncementRequest("系統維護", "今晚進行維護"));

        assertThat(response.isSuccessStatus()).isTrue();
        assertThat(response.getData().getValues()).containsEntry("recipientCount", 3);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<NotificationCreateCommand>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(notificationRepository).createAll(captor.capture());
        assertThat(captor.getValue()).hasSize(3);
    }

    @Test
    void nonAdministratorCannotBroadcastAnnouncement() {
        User vendor = userWithId(2L);
        vendor.setRole(Role.VENDOR);
        authenticate("token", "vendor@example.com", vendor);

        var response = notificationService.broadcastSystemAnnouncement(
                "Bearer token", new SystemAnnouncementRequest("系統維護", "今晚進行維護"));

        assertThat(response.getStatusCode()).isEqualTo(403);
        verify(notificationRepository, never()).createAll(any());
    }

    @Test
    void eventChangeCanFanOutToDifferentRolesUsingSameEventTarget() {
        notificationService.notifyEventChanged(
                List.of(10L, 20L, 30L),
                99L,
                "活動異動",
                "活動時間已調整");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<NotificationCreateCommand>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(notificationRepository).createAll(captor.capture());

        assertThat(captor.getValue())
                .extracting(NotificationCreateCommand::userId)
                .containsExactly(10L, 20L, 30L);
        assertThat(captor.getValue())
                .allSatisfy(command -> {
                    assertThat(command.category()).isEqualTo(NotificationCategory.EVENT_CHANGE);
                    assertThat(command.targetType()).isEqualTo(NotificationTargetType.MARKET_EVENT);
                    assertThat(command.targetId()).isEqualTo(99L);
                });
    }

    @Test
    void targetReferenceRulesAreValidatedBeforeInsert() {
        NotificationCreateCommand systemWithTarget = new NotificationCreateCommand(
                10L,
                NotificationCategory.SYSTEM,
                NotificationType.SYSTEM_ANNOUNCEMENT,
                NotificationTargetType.SYSTEM,
                99L,
                "系統公告",
                "內容");
        NotificationCreateCommand eventWithoutTarget = new NotificationCreateCommand(
                10L,
                NotificationCategory.EVENT_CHANGE,
                NotificationType.EVENT_UPDATED,
                NotificationTargetType.MARKET_EVENT,
                null,
                "活動異動",
                "內容");

        assertThatThrownBy(() -> notificationService.create(systemWithTarget))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("System notification must not have a target id");
        assertThatThrownBy(() -> notificationService.create(eventWithoutTarget))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Notification target id is required");
    }

    private NotificationCreateCommand capturedCommand() {
        ArgumentCaptor<NotificationCreateCommand> captor = ArgumentCaptor.forClass(NotificationCreateCommand.class);
        verify(notificationRepository).create(captor.capture());
        return captor.getValue();
    }

    @Test
    void markAsReadUpdatesIsReadAndReadAtForOwner() {
        User owner = userWithId(5L);
        Notification notification = notificationOwnedBy(owner, false);
        authenticate("token", "owner@example.com", owner);
        when(notificationRepo.findById(1L)).thenReturn(Optional.of(notification));

        ApiResponse<com.example.demo.dto.response.NotificationToggleDto> response =
                notificationService.markAsRead("Bearer token", 1L);

        assertThat(response.isSuccessStatus()).isTrue();
        assertThat(response.getData().id()).isEqualTo(1L);
        assertThat(response.getData().isRead()).isTrue();
        assertThat(notification.getIsRead()).isTrue();
        assertThat(notification.getReadAt()).isNotNull();
        verify(notificationRepo).save(notification);
    }

    @Test
    void markAsReadOnAlreadyReadNotificationIsIdempotent() {
        User owner = userWithId(5L);
        Notification notification = notificationOwnedBy(owner, true);
        notification.setReadAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        authenticate("token", "owner@example.com", owner);
        when(notificationRepo.findById(1L)).thenReturn(Optional.of(notification));

        ApiResponse<com.example.demo.dto.response.NotificationToggleDto> response =
                notificationService.markAsRead("Bearer token", 1L);

        assertThat(response.isSuccessStatus()).isTrue();
        assertThat(response.getData().isRead()).isTrue();
        assertThat(notification.getReadAt()).isEqualTo(LocalDateTime.of(2026, 1, 1, 0, 0));
        verify(notificationRepo, never()).save(any());
    }

    @Test
    void markAsReadReturnsFailureWhenNotificationMissing() {
        authenticate("token", "owner@example.com", userWithId(5L));
        when(notificationRepo.findById(1L)).thenReturn(Optional.empty());

        ApiResponse<com.example.demo.dto.response.NotificationToggleDto> response =
                notificationService.markAsRead("Bearer token", 1L);

        assertThat(response.isSuccessStatus()).isFalse();
        assertThat(response.getMessage()).isEqualTo("找不到通知");
        verify(notificationRepo, never()).save(any());
    }

    @Test
    void markAsReadReturnsFailureWhenNotOwner() {
        User owner = userWithId(5L);
        Notification notification = notificationOwnedBy(owner, false);
        authenticate("token", "someone-else@example.com", userWithId(9L));
        when(notificationRepo.findById(1L)).thenReturn(Optional.of(notification));

        ApiResponse<com.example.demo.dto.response.NotificationToggleDto> response =
                notificationService.markAsRead("Bearer token", 1L);

        assertThat(response.isSuccessStatus()).isFalse();
        assertThat(response.getMessage()).isEqualTo("此通知不屬於目前登入帳號");
        verify(notificationRepo, never()).save(any());
    }

    @Test
    void markAsReadReturnsFailureWhenTokenMissingOrInvalid() {
        when(jwtService.extractTokenFromAuthorizationHeader(null)).thenReturn(null);
        ApiResponse<com.example.demo.dto.response.NotificationToggleDto> missing =
                notificationService.markAsRead(null, 1L);
        assertThat(missing.isSuccessStatus()).isFalse();
        assertThat(missing.getMessage()).isEqualTo("請提供 Authorization token");

        when(jwtService.extractTokenFromAuthorizationHeader("Bearer bad")).thenReturn("bad");
        when(jwtService.isTokenValid("bad")).thenReturn(false);
        ApiResponse<com.example.demo.dto.response.NotificationToggleDto> invalid =
                notificationService.markAsRead("Bearer bad", 1L);
        assertThat(invalid.isSuccessStatus()).isFalse();
        assertThat(invalid.getMessage()).isEqualTo("Token 無效或已過期");

        verify(notificationRepo, never()).findById(any());
    }

    private void authenticate(String token, String email, User user) {
        when(jwtService.extractTokenFromAuthorizationHeader("Bearer " + token)).thenReturn(token);
        when(jwtService.isTokenValid(token)).thenReturn(true);
        when(jwtService.getEmail(token)).thenReturn(email);
        when(userRepo.findByEmail(email)).thenReturn(Optional.of(user));
    }

    private User userWithId(Long id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    private Notification notificationOwnedBy(User owner, boolean isRead) {
        Notification notification = new Notification();
        notification.setId(1L);
        notification.setUser(owner);
        notification.setIsRead(isRead);
        return notification;
    }
}
