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
import com.example.demo.dto.response.ApiResponse;
import com.example.demo.entity.Notification;
import com.example.demo.entity.User;
import com.example.demo.enums.notification.NotificationCategory;
import com.example.demo.enums.notification.NotificationTargetType;
import com.example.demo.enums.notification.NotificationType;

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

        verify(notificationRepository).create(command);
    }

    @Test
    void applicationSubmittedUsesApplicationReviewClassification() {
        notificationService.notifyApplicationSubmitted(10L, 20L, "夏日市集");

        NotificationCreateCommand command = capturedCommand();
        assertThat(command.category()).isEqualTo(NotificationCategory.APPLICATION_REVIEW);
        assertThat(command.type()).isEqualTo(NotificationType.APPLICATION_SUBMITTED);
        assertThat(command.targetType()).isEqualTo(NotificationTargetType.EVENT_APPLICATION);
        assertThat(command.targetId()).isEqualTo(20L);
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
    void blankEventTitleUsesSafeDisplayName() {
        notificationService.notifyPaymentStatusChanged(10L, 20L, " ", false);

        assertThat(capturedCommand().content()).startsWith("活動");
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
        verify(notificationRepo, never()).save(any());
    }

    @Test
    void markAsReadReturnsFailureWhenTokenMissingOrInvalid() {
        when(jwtService.extractTokenFromAuthorizationHeader(null)).thenReturn(null);
        ApiResponse<com.example.demo.dto.response.NotificationToggleDto> missing =
                notificationService.markAsRead(null, 1L);
        assertThat(missing.isSuccessStatus()).isFalse();

        when(jwtService.extractTokenFromAuthorizationHeader("Bearer bad")).thenReturn("bad");
        when(jwtService.isTokenValid("bad")).thenReturn(false);
        ApiResponse<com.example.demo.dto.response.NotificationToggleDto> invalid =
                notificationService.markAsRead("Bearer bad", 1L);
        assertThat(invalid.isSuccessStatus()).isFalse();

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
