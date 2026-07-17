package com.example.demo.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.demo.Repository.AdminLogRepo;
import com.example.demo.Repository.EventRepo;
import com.example.demo.Repository.EventStallZoneRepo;
import com.example.demo.Repository.EventUnpublishRequestRepo;
import com.example.demo.Repository.NotificationRepo;
import com.example.demo.Repository.UserRepo;
import com.example.demo.Repository.projection.admin.AdminLookupProjection;
import com.example.demo.Repository.projection.admin.EventApprovalProjection;
import com.example.demo.Repository.projection.admin.EventUnpublishReviewProjection;
import com.example.demo.Repository.projection.admin.UserAccountStatusProjection;
import com.example.demo.entity.AdminOperationLog;
import com.example.demo.entity.Notification;
import com.example.demo.entity.User;
import com.example.demo.enums.notification.NotificationCategory;
import com.example.demo.enums.notification.NotificationTargetType;
import com.example.demo.enums.notification.NotificationType;
import com.example.demo.enums.status.EventStatus;
import com.example.demo.enums.status.UnpublishRequestStatus;
import com.example.demo.enums.status.UserStatus;
import com.example.demo.enums.status.WorkflowStatus;
import com.example.demo.enums.type.AdminOperationType;
import com.example.demo.enums.type.AdminTargetType;
import com.example.demo.enums.type.Role;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {
    @Mock EventRepo eventRepo;
    @Mock EventStallZoneRepo zoneRepo;
    @Mock UserRepo userRepo;
    @Mock AdminLogRepo logRepo;
    @Mock NotificationRepo notificationRepo;
    @Mock EventUnpublishRequestRepo eventUnpublishRequestRepo;
    AdminService service;

    @BeforeEach void setUp() {
        service = new AdminService();
        ReflectionTestUtils.setField(service, "eventRepo", eventRepo);
        ReflectionTestUtils.setField(service, "eventStallZoneRepo", zoneRepo);
        ReflectionTestUtils.setField(service, "userRepo", userRepo);
        ReflectionTestUtils.setField(service, "logRepo", logRepo);
        ReflectionTestUtils.setField(service, "notificationRepo", notificationRepo);
        ReflectionTestUtils.setField(service, "eventUnpublishRequestRepo", eventUnpublishRequestRepo);
    }

    @Test void dashboardAggregatesRepositoryCounters() {
        when(eventRepo.countByWorkflowStatus(WorkflowStatus.PENDING_REVIEW)).thenReturn(1);
        when(eventRepo.countByWorkflowStatus(WorkflowStatus.MAP_BUILDING)).thenReturn(2);
        when(eventRepo.countByWorkflowStatus(WorkflowStatus.UNPUBLISH_REQUESTED)).thenReturn(3);
        when(userRepo.countByRoleAndStatus(Role.ORGANIZER, UserStatus.ACTIVE)).thenReturn(4);
        when(userRepo.countByRoleAndStatus(Role.VENDOR, UserStatus.ACTIVE)).thenReturn(5);
        when(eventRepo.countByEventInPlatform(any())).thenReturn(6);
        when(eventRepo.countByEventStatusIsACTIVE(any())).thenReturn(7);
        var result = service.getDashboardResponse();
        assertThat(result.pendingReview()).isEqualTo(1);
        assertThat(result.mapBuilding()).isEqualTo(2);
        assertThat(result.pendingUnpublish()).isEqualTo(3);
        assertThat(result.totalOrganizer()).isEqualTo(4);
        assertThat(result.totalVender()).isEqualTo(5);
        assertThat(result.totalActivity()).isEqualTo(6);
        assertThat(result.active()).isEqualTo(7);
    }

    @Test void changeStatusRejectsUnsupportedObject() {
        assertThatThrownBy(() -> service.changeToEventStatus("not an event"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void setUserAccountDisableRejectsWhenOperatorRoleIsNotAdmin() {
        assertThatThrownBy(() -> service.setUserAccountDisable(1L, "op@test.com", Role.VENDOR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("權限不足，請重新登入管理員帳號再操作");
        verifyNoInteractions(userRepo, logRepo);
    }

    @Test void setUserAccountDisableThrowsWhenAdminNotFound() {
        when(userRepo.findAdminLookupByEmailAndRole("op@test.com", Role.ADMIN)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setUserAccountDisable(1L, "op@test.com", Role.ADMIN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("找不到該管理員");
        verifyNoInteractions(logRepo);
    }

    @Test void setUserAccountDisableThrowsWhenTargetUserNotFound() {
        when(userRepo.findAdminLookupByEmailAndRole("op@test.com", Role.ADMIN))
                .thenReturn(Optional.of(new AdminLookupProjection(9L, "管理員小明")));
        when(userRepo.findAccountStatusById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setUserAccountDisable(1L, "op@test.com", Role.ADMIN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("找不到指定的使用者");
        verifyNoInteractions(logRepo);
    }

    @Test void setUserAccountDisableDisablesActiveUserAndWritesOperationLog() {
        when(userRepo.findAdminLookupByEmailAndRole("op@test.com", Role.ADMIN))
                .thenReturn(Optional.of(new AdminLookupProjection(9L, "管理員小明")));
        when(userRepo.findAccountStatusById(1L))
                .thenReturn(Optional.of(new UserAccountStatusProjection(1L, UserStatus.ACTIVE, "vendor@test.com", "攤主小華")));
        User adminRef = new User();
        when(userRepo.getReferenceById(9L)).thenReturn(adminRef);

        var result = service.setUserAccountDisable(1L, "op@test.com", Role.ADMIN);

        verify(userRepo).updateStatusIfCurrent(1L, UserStatus.ACTIVE, UserStatus.DISABLED);
        assertThat(result.userName()).isEqualTo("攤主小華");
        assertThat(result.userEmail()).isEqualTo("vendor@test.com");
        assertThat(result.newAccountStatus()).isEqualTo(UserStatus.DISABLED);

        ArgumentCaptor<AdminOperationLog> captor = ArgumentCaptor.forClass(AdminOperationLog.class);
        verify(logRepo).save(captor.capture());
        AdminOperationLog savedLog = captor.getValue();
        assertThat(savedLog.getUser()).isSameAs(adminRef);
        assertThat(savedLog.getOperationType()).isEqualTo(AdminOperationType.ACCOUNT_DISABLED);
        assertThat(savedLog.getTargetType()).isEqualTo(AdminTargetType.USER);
        assertThat(savedLog.getTargetId()).isEqualTo(1L);
        assertThat(savedLog.getTargetLabel()).isEqualTo("攤主小華");
        assertThat(savedLog.getContent()).isEqualTo("管理員小明停用攤主小華的帳號");
    }

    @Test void setUserAccountDisableSkipsUpdateWhenTargetAlreadyNotActiveButStillLogs() {
        when(userRepo.findAdminLookupByEmailAndRole("op@test.com", Role.ADMIN))
                .thenReturn(Optional.of(new AdminLookupProjection(9L, "管理員小明")));
        when(userRepo.findAccountStatusById(1L))
                .thenReturn(Optional.of(new UserAccountStatusProjection(1L, UserStatus.DISABLED, "vendor@test.com", "攤主小華")));
        when(userRepo.getReferenceById(9L)).thenReturn(new User());

        var result = service.setUserAccountDisable(1L, "op@test.com", Role.ADMIN);

        verify(userRepo, never()).updateStatusIfCurrent(any(), any(), any());
        assertThat(result.newAccountStatus()).isEqualTo(UserStatus.DISABLED);
        verify(logRepo).save(any(AdminOperationLog.class));
    }

    @Test void setUserAccountDisableFallsBackToEmailWhenContactNameMissing() {
        when(userRepo.findAdminLookupByEmailAndRole("op@test.com", Role.ADMIN))
                .thenReturn(Optional.of(new AdminLookupProjection(9L, "管理員小明")));
        when(userRepo.findAccountStatusById(1L))
                .thenReturn(Optional.of(new UserAccountStatusProjection(1L, UserStatus.ACTIVE, "vendor@test.com", null)));
        when(userRepo.getReferenceById(9L)).thenReturn(new User());

        var result = service.setUserAccountDisable(1L, "op@test.com", Role.ADMIN);

        assertThat(result.userName()).isNull();
        ArgumentCaptor<AdminOperationLog> captor = ArgumentCaptor.forClass(AdminOperationLog.class);
        verify(logRepo).save(captor.capture());
        assertThat(captor.getValue().getTargetLabel()).isEqualTo("vendor@test.com");
    }

    @Test void setUserAccountRestoreRejectsWhenOperatorRoleIsNotAdmin() {
        assertThatThrownBy(() -> service.setUserAccountRestore(1L, "op@test.com", Role.VENDOR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("權限不足，請重新登入管理員帳號再操作");
        verifyNoInteractions(userRepo, logRepo);
    }

    @Test void setUserAccountRestoreThrowsWhenAdminNotFound() {
        when(userRepo.findAdminLookupByEmailAndRole("op@test.com", Role.ADMIN)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setUserAccountRestore(1L, "op@test.com", Role.ADMIN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("找不到該管理員");
        verifyNoInteractions(logRepo);
    }

    @Test void setUserAccountRestoreThrowsWhenTargetUserNotFound() {
        when(userRepo.findAdminLookupByEmailAndRole("op@test.com", Role.ADMIN))
                .thenReturn(Optional.of(new AdminLookupProjection(9L, "管理員小明")));
        when(userRepo.findAccountStatusById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setUserAccountRestore(1L, "op@test.com", Role.ADMIN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("找不到指定的使用者");
        verifyNoInteractions(logRepo);
    }

    @Test void setUserAccountRestoreRestoresDisabledUserAndWritesOperationLog() {
        when(userRepo.findAdminLookupByEmailAndRole("op@test.com", Role.ADMIN))
                .thenReturn(Optional.of(new AdminLookupProjection(9L, "管理員小明")));
        when(userRepo.findAccountStatusById(1L))
                .thenReturn(Optional.of(new UserAccountStatusProjection(1L, UserStatus.DISABLED, "vendor@test.com", "攤主小華")));
        User adminRef = new User();
        when(userRepo.getReferenceById(9L)).thenReturn(adminRef);

        var result = service.setUserAccountRestore(1L, "op@test.com", Role.ADMIN);

        verify(userRepo).updateStatusIfCurrent(1L, UserStatus.DISABLED, UserStatus.ACTIVE);
        assertThat(result.userName()).isEqualTo("攤主小華");
        assertThat(result.userEmail()).isEqualTo("vendor@test.com");
        assertThat(result.newAccountStatus()).isEqualTo(UserStatus.ACTIVE);

        ArgumentCaptor<AdminOperationLog> captor = ArgumentCaptor.forClass(AdminOperationLog.class);
        verify(logRepo).save(captor.capture());
        AdminOperationLog savedLog = captor.getValue();
        assertThat(savedLog.getUser()).isSameAs(adminRef);
        assertThat(savedLog.getOperationType()).isEqualTo(AdminOperationType.ACCOUNT_RESTORED);
        assertThat(savedLog.getTargetType()).isEqualTo(AdminTargetType.USER);
        assertThat(savedLog.getTargetId()).isEqualTo(1L);
        assertThat(savedLog.getTargetLabel()).isEqualTo("攤主小華");
        assertThat(savedLog.getContent()).isEqualTo("管理員小明恢復攤主小華的帳號");
    }

    @Test void setUserAccountRestoreSkipsUpdateWhenTargetAlreadyNotDisabledButStillLogs() {
        when(userRepo.findAdminLookupByEmailAndRole("op@test.com", Role.ADMIN))
                .thenReturn(Optional.of(new AdminLookupProjection(9L, "管理員小明")));
        when(userRepo.findAccountStatusById(1L))
                .thenReturn(Optional.of(new UserAccountStatusProjection(1L, UserStatus.ACTIVE, "vendor@test.com", "攤主小華")));
        when(userRepo.getReferenceById(9L)).thenReturn(new User());

        var result = service.setUserAccountRestore(1L, "op@test.com", Role.ADMIN);

        verify(userRepo, never()).updateStatusIfCurrent(any(), any(), any());
        assertThat(result.newAccountStatus()).isEqualTo(UserStatus.ACTIVE);
        verify(logRepo).save(any(AdminOperationLog.class));
    }

    @Test void setUserAccountRestoreFallsBackToEmailWhenContactNameMissing() {
        when(userRepo.findAdminLookupByEmailAndRole("op@test.com", Role.ADMIN))
                .thenReturn(Optional.of(new AdminLookupProjection(9L, "管理員小明")));
        when(userRepo.findAccountStatusById(1L))
                .thenReturn(Optional.of(new UserAccountStatusProjection(1L, UserStatus.DISABLED, "vendor@test.com", null)));
        when(userRepo.getReferenceById(9L)).thenReturn(new User());

        var result = service.setUserAccountRestore(1L, "op@test.com", Role.ADMIN);

        assertThat(result.userName()).isNull();
        ArgumentCaptor<AdminOperationLog> captor = ArgumentCaptor.forClass(AdminOperationLog.class);
        verify(logRepo).save(captor.capture());
        assertThat(captor.getValue().getTargetLabel()).isEqualTo("vendor@test.com");
    }

    @Test void setEventApproveRejectsWhenOperatorRoleIsNotAdmin() {
        assertThatThrownBy(() -> service.setEventApprove(1L, "op@test.com", Role.ORGANIZER, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("權限不足，請重新登入管理員帳號再操作");
        verifyNoInteractions(userRepo, eventRepo, logRepo, notificationRepo);
    }

    @Test void setEventApproveThrowsWhenAdminNotFound() {
        when(userRepo.findAdminLookupByEmailAndRole("op@test.com", Role.ADMIN)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setEventApprove(1L, "op@test.com", Role.ADMIN, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("找不到該管理員");
        verifyNoInteractions(eventRepo, logRepo, notificationRepo);
    }

    @Test void setEventApproveThrowsWhenEventNotFound() {
        when(userRepo.findAdminLookupByEmailAndRole("op@test.com", Role.ADMIN))
                .thenReturn(Optional.of(new AdminLookupProjection(9L, "管理員小明")));
        when(eventRepo.findApprovalStatusById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setEventApprove(1L, "op@test.com", Role.ADMIN, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("找不到指定的活動");
        verifyNoInteractions(logRepo, notificationRepo);
    }

    @Test void setEventApproveThrowsWhenEventNotPendingReview() {
        when(userRepo.findAdminLookupByEmailAndRole("op@test.com", Role.ADMIN))
                .thenReturn(Optional.of(new AdminLookupProjection(9L, "管理員小明")));
        when(eventRepo.findApprovalStatusById(1L))
                .thenReturn(Optional.of(new EventApprovalProjection(1L, WorkflowStatus.DRAFT, "夏日市集", 5L, "王小華")));

        assertThatThrownBy(() -> service.setEventApprove(1L, "op@test.com", Role.ADMIN, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("夏日市集當前狀態不可執行此操作");
        verify(eventRepo, never()).updateWorkflowStatusIfCurrent(any(), any(), any());
        verifyNoInteractions(logRepo, notificationRepo);
    }

    @Test void setEventApproveApprovesPendingReviewEventAndWritesNotificationAndOperationLog() {
        when(userRepo.findAdminLookupByEmailAndRole("op@test.com", Role.ADMIN))
                .thenReturn(Optional.of(new AdminLookupProjection(9L, "管理員小明")));
        when(eventRepo.findApprovalStatusById(1L))
                .thenReturn(Optional.of(new EventApprovalProjection(1L, WorkflowStatus.PENDING_REVIEW, "夏日市集", 5L, "王小華")));
        User adminRef = new User();
        User organizerRef = new User();
        when(userRepo.getReferenceById(9L)).thenReturn(adminRef);
        when(userRepo.getReferenceById(5L)).thenReturn(organizerRef);

        var result = service.setEventApprove(1L, "op@test.com", Role.ADMIN, null);

        verify(eventRepo).updateWorkflowStatusIfCurrent(1L, WorkflowStatus.PENDING_REVIEW, WorkflowStatus.MAP_BUILDING);
        verify(eventRepo, never()).updateWorkflowStatusAndReviewNoteIfCurrent(any(), any(), any(), any());
        assertThat(result.eventName()).isEqualTo("夏日市集");
        assertThat(result.newEventStatus()).isEqualTo(EventStatus.MAP_BUILDING);

        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepo).save(notificationCaptor.capture());
        Notification savedNotification = notificationCaptor.getValue();
        assertThat(savedNotification.getUser()).isSameAs(organizerRef);
        assertThat(savedNotification.getCategory()).isEqualTo(NotificationCategory.EVENT_CHANGE);
        assertThat(savedNotification.getType()).isEqualTo(NotificationType.EVENT_APPROVED);
        assertThat(savedNotification.getTargetType()).isEqualTo(NotificationTargetType.MARKET_EVENT);
        assertThat(savedNotification.getTargetId()).isEqualTo(1L);
        assertThat(savedNotification.getTitle()).isEqualTo("審核通過");
        assertThat(savedNotification.getContent()).isEqualTo("夏日市集審核通過，開始建置攤位地圖");

        ArgumentCaptor<AdminOperationLog> logCaptor = ArgumentCaptor.forClass(AdminOperationLog.class);
        verify(logRepo).save(logCaptor.capture());
        AdminOperationLog savedLog = logCaptor.getValue();
        assertThat(savedLog.getUser()).isSameAs(adminRef);
        assertThat(savedLog.getOperationType()).isEqualTo(AdminOperationType.ACTIVITY_REVIEW);
        assertThat(savedLog.getTargetType()).isEqualTo(AdminTargetType.MARKET_EVENT);
        assertThat(savedLog.getTargetId()).isEqualTo(1L);
        assertThat(savedLog.getTargetLabel()).isEqualTo("夏日市集");
        assertThat(savedLog.getContent()).isEqualTo("管理員小明同意夏日市集申請");
    }

    @Test void setEventApproveWithNoteUpdatesMarketEventReviewNote() {
        when(userRepo.findAdminLookupByEmailAndRole("op@test.com", Role.ADMIN))
                .thenReturn(Optional.of(new AdminLookupProjection(9L, "管理員小明")));
        when(eventRepo.findApprovalStatusById(1L))
                .thenReturn(Optional.of(new EventApprovalProjection(1L, WorkflowStatus.PENDING_REVIEW, "夏日市集", 5L, "王小華")));
        when(userRepo.getReferenceById(9L)).thenReturn(new User());
        when(userRepo.getReferenceById(5L)).thenReturn(new User());

        service.setEventApprove(1L, "op@test.com", Role.ADMIN, "已補充審查資料");

        verify(eventRepo).updateWorkflowStatusAndReviewNoteIfCurrent(
                1L, WorkflowStatus.PENDING_REVIEW, WorkflowStatus.MAP_BUILDING, "已補充審查資料");
        verify(eventRepo, never()).updateWorkflowStatusIfCurrent(any(), any(), any());
    }

    @Test void setEventRevisionRejectsWhenOperatorRoleIsNotAdmin() {
        assertThatThrownBy(() -> service.setEventRevision(1L, "op@test.com", Role.ORGANIZER, "缺少營業執照"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("權限不足，請重新登入管理員帳號再操作");
        verifyNoInteractions(userRepo, eventRepo, logRepo, notificationRepo);
    }

    @Test void setEventRevisionThrowsWhenAdminNotFound() {
        when(userRepo.findAdminLookupByEmailAndRole("op@test.com", Role.ADMIN)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setEventRevision(1L, "op@test.com", Role.ADMIN, "缺少營業執照"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("找不到該管理員");
        verifyNoInteractions(eventRepo, logRepo, notificationRepo);
    }

    @Test void setEventRevisionThrowsWhenNoteIsBlank() {
        when(userRepo.findAdminLookupByEmailAndRole("op@test.com", Role.ADMIN))
                .thenReturn(Optional.of(new AdminLookupProjection(9L, "管理員小明")));

        assertThatThrownBy(() -> service.setEventRevision(1L, "op@test.com", Role.ADMIN, "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("請提供補件原因");
        verifyNoInteractions(eventRepo, logRepo, notificationRepo);
    }

    @Test void setEventRevisionThrowsWhenEventNotFound() {
        when(userRepo.findAdminLookupByEmailAndRole("op@test.com", Role.ADMIN))
                .thenReturn(Optional.of(new AdminLookupProjection(9L, "管理員小明")));
        when(eventRepo.findApprovalStatusById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setEventRevision(1L, "op@test.com", Role.ADMIN, "缺少營業執照"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("找不到指定的活動");
        verifyNoInteractions(logRepo, notificationRepo);
    }

    @Test void setEventRevisionThrowsWhenEventNotPendingReview() {
        when(userRepo.findAdminLookupByEmailAndRole("op@test.com", Role.ADMIN))
                .thenReturn(Optional.of(new AdminLookupProjection(9L, "管理員小明")));
        when(eventRepo.findApprovalStatusById(1L))
                .thenReturn(Optional.of(new EventApprovalProjection(1L, WorkflowStatus.DRAFT, "夏日市集", 5L, "王小華")));

        assertThatThrownBy(() -> service.setEventRevision(1L, "op@test.com", Role.ADMIN, "缺少營業執照"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("夏日市集當前狀態不可執行此操作");
        verify(eventRepo, never()).updateWorkflowStatusAndReviewNoteIfCurrent(any(), any(), any(), any());
        verifyNoInteractions(logRepo, notificationRepo);
    }

    @Test void setEventRevisionRequiresRevisionForPendingReviewEventAndWritesNotificationAndOperationLog() {
        when(userRepo.findAdminLookupByEmailAndRole("op@test.com", Role.ADMIN))
                .thenReturn(Optional.of(new AdminLookupProjection(9L, "管理員小明")));
        when(eventRepo.findApprovalStatusById(1L))
                .thenReturn(Optional.of(new EventApprovalProjection(1L, WorkflowStatus.PENDING_REVIEW, "夏日市集", 5L, "王小華")));
        User adminRef = new User();
        User organizerRef = new User();
        when(userRepo.getReferenceById(9L)).thenReturn(adminRef);
        when(userRepo.getReferenceById(5L)).thenReturn(organizerRef);

        var result = service.setEventRevision(1L, "op@test.com", Role.ADMIN, "缺少營業執照");

        verify(eventRepo).updateWorkflowStatusAndReviewNoteIfCurrent(
                1L, WorkflowStatus.PENDING_REVIEW, WorkflowStatus.REVISION_REQUIRED, "缺少營業執照");
        assertThat(result.eventName()).isEqualTo("夏日市集");
        assertThat(result.newEventStatus()).isEqualTo(EventStatus.REVISION_REQUIRED);

        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepo).save(notificationCaptor.capture());
        Notification savedNotification = notificationCaptor.getValue();
        assertThat(savedNotification.getUser()).isSameAs(organizerRef);
        assertThat(savedNotification.getCategory()).isEqualTo(NotificationCategory.EVENT_CHANGE);
        assertThat(savedNotification.getType()).isEqualTo(NotificationType.EVENT_REVISION_REQUIRED);
        assertThat(savedNotification.getTargetType()).isEqualTo(NotificationTargetType.MARKET_EVENT);
        assertThat(savedNotification.getTargetId()).isEqualTo(1L);
        assertThat(savedNotification.getTitle()).isEqualTo("補件通知");
        assertThat(savedNotification.getContent()).isEqualTo("夏日市集需要補件，請修改後重新送出審核");

        ArgumentCaptor<AdminOperationLog> logCaptor = ArgumentCaptor.forClass(AdminOperationLog.class);
        verify(logRepo).save(logCaptor.capture());
        AdminOperationLog savedLog = logCaptor.getValue();
        assertThat(savedLog.getUser()).isSameAs(adminRef);
        assertThat(savedLog.getOperationType()).isEqualTo(AdminOperationType.REQUEST_REVISION);
        assertThat(savedLog.getTargetType()).isEqualTo(AdminTargetType.MARKET_EVENT);
        assertThat(savedLog.getTargetId()).isEqualTo(1L);
        assertThat(savedLog.getTargetLabel()).isEqualTo("夏日市集");
        assertThat(savedLog.getContent()).isEqualTo("管理員小明退回夏日市集申請, 原因:缺少營業執照");
    }

    @Test void setEventMapCompleteRejectsWhenOperatorRoleIsNotAdmin() {
        assertThatThrownBy(() -> service.setEventMapComplete(1L, "op@test.com", Role.ORGANIZER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("權限不足，請重新登入管理員帳號再操作");
        verifyNoInteractions(userRepo, eventRepo, logRepo, notificationRepo);
    }

    @Test void setEventMapCompleteThrowsWhenAdminNotFound() {
        when(userRepo.findAdminLookupByEmailAndRole("op@test.com", Role.ADMIN)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setEventMapComplete(1L, "op@test.com", Role.ADMIN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("找不到該管理員");
        verifyNoInteractions(eventRepo, logRepo, notificationRepo);
    }

    @Test void setEventMapCompleteThrowsWhenEventNotFound() {
        when(userRepo.findAdminLookupByEmailAndRole("op@test.com", Role.ADMIN))
                .thenReturn(Optional.of(new AdminLookupProjection(9L, "管理員小明")));
        when(eventRepo.findApprovalStatusById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setEventMapComplete(1L, "op@test.com", Role.ADMIN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("找不到指定的活動");
        verifyNoInteractions(logRepo, notificationRepo);
    }

    @Test void setEventMapCompleteThrowsWhenEventNotMapBuilding() {
        when(userRepo.findAdminLookupByEmailAndRole("op@test.com", Role.ADMIN))
                .thenReturn(Optional.of(new AdminLookupProjection(9L, "管理員小明")));
        when(eventRepo.findApprovalStatusById(1L))
                .thenReturn(Optional.of(new EventApprovalProjection(1L, WorkflowStatus.PENDING_REVIEW, "夏日市集", 5L, "王小華")));

        assertThatThrownBy(() -> service.setEventMapComplete(1L, "op@test.com", Role.ADMIN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("夏日市集當前狀態不可執行此操作");
        verify(eventRepo, never()).updateWorkflowStatusIfCurrent(any(), any(), any());
        verifyNoInteractions(logRepo, notificationRepo);
    }

    @Test void setEventMapCompleteCompletesMapBuildingEventAndWritesNotificationAndOperationLog() {
        when(userRepo.findAdminLookupByEmailAndRole("op@test.com", Role.ADMIN))
                .thenReturn(Optional.of(new AdminLookupProjection(9L, "管理員小明")));
        when(eventRepo.findApprovalStatusById(1L))
                .thenReturn(Optional.of(new EventApprovalProjection(1L, WorkflowStatus.MAP_BUILDING, "夏日市集", 5L, "王小華")));
        User adminRef = new User();
        User organizerRef = new User();
        when(userRepo.getReferenceById(9L)).thenReturn(adminRef);
        when(userRepo.getReferenceById(5L)).thenReturn(organizerRef);

        var result = service.setEventMapComplete(1L, "op@test.com", Role.ADMIN);

        verify(eventRepo).updateWorkflowStatusIfCurrent(1L, WorkflowStatus.MAP_BUILDING, WorkflowStatus.READY_TO_PUBLISH);
        assertThat(result.eventName()).isEqualTo("夏日市集");
        assertThat(result.newEventStatus()).isEqualTo(EventStatus.READY_TO_PUBLISH);

        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepo).save(notificationCaptor.capture());
        Notification savedNotification = notificationCaptor.getValue();
        assertThat(savedNotification.getUser()).isSameAs(organizerRef);
        assertThat(savedNotification.getCategory()).isEqualTo(NotificationCategory.EVENT_CHANGE);
        assertThat(savedNotification.getType()).isEqualTo(NotificationType.EVENT_MAP_COMPLETED);
        assertThat(savedNotification.getTargetType()).isEqualTo(NotificationTargetType.MARKET_EVENT);
        assertThat(savedNotification.getTargetId()).isEqualTo(1L);
        assertThat(savedNotification.getTitle()).isEqualTo("地圖完成");
        assertThat(savedNotification.getContent()).isEqualTo("夏日市集攤位地圖已建置完成，可前往活動詳情確認");

        ArgumentCaptor<AdminOperationLog> logCaptor = ArgumentCaptor.forClass(AdminOperationLog.class);
        verify(logRepo).save(logCaptor.capture());
        AdminOperationLog savedLog = logCaptor.getValue();
        assertThat(savedLog.getUser()).isSameAs(adminRef);
        assertThat(savedLog.getOperationType()).isEqualTo(AdminOperationType.MAP_BUILD_COMPLETED);
        assertThat(savedLog.getTargetType()).isEqualTo(AdminTargetType.MARKET_EVENT);
        assertThat(savedLog.getTargetId()).isEqualTo(1L);
        assertThat(savedLog.getTargetLabel()).isEqualTo("夏日市集");
        assertThat(savedLog.getContent()).isEqualTo("管理員小明通知主辦方王小華 夏日市集地圖建置完成");
    }

    @Test void setEventMapCompleteFallsBackToDefaultLabelsWhenOrganizerContactNameMissing() {
        when(userRepo.findAdminLookupByEmailAndRole("op@test.com", Role.ADMIN))
                .thenReturn(Optional.of(new AdminLookupProjection(9L, "管理員小明")));
        when(eventRepo.findApprovalStatusById(1L))
                .thenReturn(Optional.of(new EventApprovalProjection(1L, WorkflowStatus.MAP_BUILDING, "夏日市集", 5L, null)));
        when(userRepo.getReferenceById(9L)).thenReturn(new User());
        when(userRepo.getReferenceById(5L)).thenReturn(new User());

        service.setEventMapComplete(1L, "op@test.com", Role.ADMIN);

        ArgumentCaptor<AdminOperationLog> logCaptor = ArgumentCaptor.forClass(AdminOperationLog.class);
        verify(logRepo).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getContent()).isEqualTo("管理員小明通知主辦方主辦方 夏日市集地圖建置完成");
    }

    @Test void setEventUnpublishRejectsWhenOperatorRoleIsNotAdmin() {
        assertThatThrownBy(() -> service.setEventUnpublish(1L, "op@test.com", Role.ORGANIZER, "庫存已清空"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("權限不足，請重新登入管理員帳號再操作");
        verifyNoInteractions(userRepo, eventRepo, logRepo, notificationRepo, eventUnpublishRequestRepo);
    }

    @Test void setEventUnpublishThrowsWhenAdminNotFound() {
        when(userRepo.findAdminLookupByEmailAndRole("op@test.com", Role.ADMIN)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setEventUnpublish(1L, "op@test.com", Role.ADMIN, "庫存已清空"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("找不到該管理員");
        verifyNoInteractions(eventRepo, logRepo, notificationRepo, eventUnpublishRequestRepo);
    }

    @Test void setEventUnpublishThrowsWhenEventNotFound() {
        when(userRepo.findAdminLookupByEmailAndRole("op@test.com", Role.ADMIN))
                .thenReturn(Optional.of(new AdminLookupProjection(9L, "管理員小明")));
        when(eventRepo.findApprovalStatusById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setEventUnpublish(1L, "op@test.com", Role.ADMIN, "庫存已清空"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("找不到指定的活動");
        verifyNoInteractions(logRepo, notificationRepo, eventUnpublishRequestRepo);
    }

    @Test void setEventUnpublishThrowsWhenEventNotUnpublishRequested() {
        when(userRepo.findAdminLookupByEmailAndRole("op@test.com", Role.ADMIN))
                .thenReturn(Optional.of(new AdminLookupProjection(9L, "管理員小明")));
        when(eventRepo.findApprovalStatusById(1L))
                .thenReturn(Optional.of(new EventApprovalProjection(1L, WorkflowStatus.MAP_BUILDING, "夏日市集", 5L, "王小華")));

        assertThatThrownBy(() -> service.setEventUnpublish(1L, "op@test.com", Role.ADMIN, "庫存已清空"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("夏日市集當前狀態不可執行此操作");
        verifyNoInteractions(logRepo, notificationRepo, eventUnpublishRequestRepo);
    }

    @Test void setEventUnpublishThrowsAndNotifiesAdminWhenNoPendingRequestFound() {
        when(userRepo.findAdminLookupByEmailAndRole("op@test.com", Role.ADMIN))
                .thenReturn(Optional.of(new AdminLookupProjection(9L, "管理員小明")));
        when(eventRepo.findApprovalStatusById(1L))
                .thenReturn(Optional.of(new EventApprovalProjection(1L, WorkflowStatus.UNPUBLISH_REQUESTED, "夏日市集", 5L, "王小華")));
        when(eventUnpublishRequestRepo.findLatestRequestIdByEventIdAndStatus(1L, UnpublishRequestStatus.PENDING))
                .thenReturn(Optional.empty());
        User adminRef = new User();
        when(userRepo.getReferenceById(9L)).thenReturn(adminRef);

        assertThatThrownBy(() -> service.setEventUnpublish(1L, "op@test.com", Role.ADMIN, "庫存已清空"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("找不到該活動的下架申請");

        verify(eventRepo, never()).updateWorkflowStatusIfCurrent(any(), any(), any());
        verify(eventUnpublishRequestRepo, never()).reviewIfCurrent(any(), any(), any(), any(), any());
        verify(logRepo, never()).save(any());

        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepo).save(notificationCaptor.capture());
        Notification savedNotification = notificationCaptor.getValue();
        assertThat(savedNotification.getUser()).isSameAs(adminRef);
        assertThat(savedNotification.getCategory()).isEqualTo(NotificationCategory.EXCEPTION);
        assertThat(savedNotification.getType()).isEqualTo(NotificationType.SYSTEM_EXCEPTION);
        assertThat(savedNotification.getTargetType()).isEqualTo(NotificationTargetType.MARKET_EVENT);
        assertThat(savedNotification.getTargetId()).isEqualTo(1L);
        assertThat(savedNotification.getTitle()).isEqualTo("活動狀態異常");
        assertThat(savedNotification.getContent()).isEqualTo("夏日市集活動狀態為申請下架，資料庫查無該活動下架申請單");
    }

    @Test void setEventUnpublishApprovesRequestAndWritesNotificationAndOperationLog() {
        when(userRepo.findAdminLookupByEmailAndRole("op@test.com", Role.ADMIN))
                .thenReturn(Optional.of(new AdminLookupProjection(9L, "管理員小明")));
        when(eventRepo.findApprovalStatusById(1L))
                .thenReturn(Optional.of(new EventApprovalProjection(1L, WorkflowStatus.UNPUBLISH_REQUESTED, "夏日市集", 5L, "王小華")));
        when(eventUnpublishRequestRepo.findLatestRequestIdByEventIdAndStatus(1L, UnpublishRequestStatus.PENDING))
                .thenReturn(Optional.of(77L));
        User adminRef = new User();
        User organizerRef = new User();
        when(userRepo.getReferenceById(9L)).thenReturn(adminRef);
        when(userRepo.getReferenceById(5L)).thenReturn(organizerRef);

        var result = service.setEventUnpublish(1L, "op@test.com", Role.ADMIN, "庫存已清空");

        verify(eventRepo).updateWorkflowStatusIfCurrent(1L, WorkflowStatus.UNPUBLISH_REQUESTED, WorkflowStatus.UNPUBLISHED);
        verify(eventUnpublishRequestRepo).reviewIfCurrent(
                77L, adminRef, UnpublishRequestStatus.PENDING, UnpublishRequestStatus.APPROVED, "庫存已清空");
        assertThat(result.eventName()).isEqualTo("夏日市集");
        assertThat(result.newEventStatus()).isEqualTo(EventStatus.UNPUBLISHED);

        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepo).save(notificationCaptor.capture());
        Notification savedNotification = notificationCaptor.getValue();
        assertThat(savedNotification.getUser()).isSameAs(organizerRef);
        assertThat(savedNotification.getCategory()).isEqualTo(NotificationCategory.EVENT_CHANGE);
        assertThat(savedNotification.getType()).isEqualTo(NotificationType.EVENT_UNPUBLISHED);
        assertThat(savedNotification.getTargetType()).isEqualTo(NotificationTargetType.MARKET_EVENT);
        assertThat(savedNotification.getTargetId()).isEqualTo(1L);
        assertThat(savedNotification.getTitle()).isEqualTo("活動下架");
        assertThat(savedNotification.getContent()).isEqualTo("夏日市集活動已下架");

        ArgumentCaptor<AdminOperationLog> logCaptor = ArgumentCaptor.forClass(AdminOperationLog.class);
        verify(logRepo).save(logCaptor.capture());
        AdminOperationLog savedLog = logCaptor.getValue();
        assertThat(savedLog.getUser()).isSameAs(adminRef);
        assertThat(savedLog.getOperationType()).isEqualTo(AdminOperationType.EVENT_UNPUBLISH_REVIEW);
        assertThat(savedLog.getTargetType()).isEqualTo(AdminTargetType.EVENT_UNPUBLISH_REQUEST);
        assertThat(savedLog.getTargetId()).isEqualTo(77L);
        assertThat(savedLog.getTargetLabel()).isEqualTo("夏日市集");
        assertThat(savedLog.getContent()).isEqualTo("管理員小明審核通過夏日市集活動下架申請");
    }

    @Test void setEventUnpublishRequestRejectRejectsWhenOperatorRoleIsNotAdmin() {
        assertThatThrownBy(() -> service.setEventUnpublishRequestReject(77L, "op@test.com", Role.ORGANIZER, "缺少營業執照"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("權限不足，請重新登入管理員帳號再操作");
        verifyNoInteractions(userRepo, eventRepo, logRepo, notificationRepo, eventUnpublishRequestRepo);
    }

    @Test void setEventUnpublishRequestRejectThrowsWhenAdminNotFound() {
        when(userRepo.findAdminLookupByEmailAndRole("op@test.com", Role.ADMIN)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setEventUnpublishRequestReject(77L, "op@test.com", Role.ADMIN, "缺少營業執照"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("找不到該管理員");
        verifyNoInteractions(eventRepo, logRepo, notificationRepo, eventUnpublishRequestRepo);
    }

    @Test void setEventUnpublishRequestRejectThrowsWhenNoteIsBlank() {
        when(userRepo.findAdminLookupByEmailAndRole("op@test.com", Role.ADMIN))
                .thenReturn(Optional.of(new AdminLookupProjection(9L, "管理員小明")));

        assertThatThrownBy(() -> service.setEventUnpublishRequestReject(77L, "op@test.com", Role.ADMIN, "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("請提供補件原因");
        verifyNoInteractions(eventRepo, logRepo, notificationRepo, eventUnpublishRequestRepo);
    }

    @Test void setEventUnpublishRequestRejectThrowsWhenRequestNotFound() {
        when(userRepo.findAdminLookupByEmailAndRole("op@test.com", Role.ADMIN))
                .thenReturn(Optional.of(new AdminLookupProjection(9L, "管理員小明")));
        when(eventUnpublishRequestRepo.findReviewInfoById(77L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setEventUnpublishRequestReject(77L, "op@test.com", Role.ADMIN, "缺少營業執照"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("找不到指定的下架申請");
        verifyNoInteractions(eventRepo, logRepo, notificationRepo);
        verify(eventUnpublishRequestRepo, never()).reviewIfCurrent(any(), any(), any(), any(), any());
    }

    @Test void setEventUnpublishRequestRejectThrowsWhenRequestNotPending() {
        when(userRepo.findAdminLookupByEmailAndRole("op@test.com", Role.ADMIN))
                .thenReturn(Optional.of(new AdminLookupProjection(9L, "管理員小明")));
        when(eventUnpublishRequestRepo.findReviewInfoById(77L)).thenReturn(Optional.of(
                new EventUnpublishReviewProjection(
                        UnpublishRequestStatus.REJECTED, 1L, WorkflowStatus.UNPUBLISH_REQUESTED, "夏日市集", null, 5L)));

        assertThatThrownBy(() -> service.setEventUnpublishRequestReject(77L, "op@test.com", Role.ADMIN, "缺少營業執照"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("該下架申請單不可進行此操作");
        verifyNoInteractions(eventRepo, logRepo, notificationRepo);
        verify(eventUnpublishRequestRepo, never()).reviewIfCurrent(any(), any(), any(), any(), any());
    }

    @Test void setEventUnpublishRequestRejectThrowsWhenEventIdIsNull() {
        when(userRepo.findAdminLookupByEmailAndRole("op@test.com", Role.ADMIN))
                .thenReturn(Optional.of(new AdminLookupProjection(9L, "管理員小明")));
        when(eventUnpublishRequestRepo.findReviewInfoById(77L)).thenReturn(Optional.of(
                new EventUnpublishReviewProjection(
                        UnpublishRequestStatus.PENDING, null, WorkflowStatus.UNPUBLISH_REQUESTED, "夏日市集", null, 5L)));

        assertThatThrownBy(() -> service.setEventUnpublishRequestReject(77L, "op@test.com", Role.ADMIN, "缺少營業執照"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("找不到該下架申請對應的活動");
        verifyNoInteractions(eventRepo, logRepo, notificationRepo);
        verify(eventUnpublishRequestRepo, never()).reviewIfCurrent(any(), any(), any(), any(), any());
    }

    @Test void setEventUnpublishRequestRejectThrowsWhenEventNotUnpublishRequested() {
        when(userRepo.findAdminLookupByEmailAndRole("op@test.com", Role.ADMIN))
                .thenReturn(Optional.of(new AdminLookupProjection(9L, "管理員小明")));
        when(eventUnpublishRequestRepo.findReviewInfoById(77L)).thenReturn(Optional.of(
                new EventUnpublishReviewProjection(
                        UnpublishRequestStatus.PENDING, 1L, WorkflowStatus.PUBLISHED, "夏日市集", null, 5L)));

        assertThatThrownBy(() -> service.setEventUnpublishRequestReject(77L, "op@test.com", Role.ADMIN, "缺少營業執照"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("夏日市集當前狀態不可執行此操作");
        verify(eventRepo, never()).updateWorkflowStatusIfCurrent(any(), any(), any());
        verifyNoInteractions(logRepo, notificationRepo);
        verify(eventUnpublishRequestRepo, never()).reviewIfCurrent(any(), any(), any(), any(), any());
    }

    @Test void setEventUnpublishRequestRejectRestoresToPublishedWhenBrandNotYetPublicAndWritesNotificationAndOperationLog() {
        when(userRepo.findAdminLookupByEmailAndRole("op@test.com", Role.ADMIN))
                .thenReturn(Optional.of(new AdminLookupProjection(9L, "管理員小明")));
        when(eventUnpublishRequestRepo.findReviewInfoById(77L)).thenReturn(Optional.of(
                new EventUnpublishReviewProjection(
                        UnpublishRequestStatus.PENDING, 1L, WorkflowStatus.UNPUBLISH_REQUESTED, "夏日市集", null, 5L)));
        User adminRef = new User();
        User organizerRef = new User();
        when(userRepo.getReferenceById(9L)).thenReturn(adminRef);
        when(userRepo.getReferenceById(5L)).thenReturn(organizerRef);

        var result = service.setEventUnpublishRequestReject(77L, "op@test.com", Role.ADMIN, "缺少營業執照");

        verify(eventRepo).updateWorkflowStatusIfCurrent(1L, WorkflowStatus.UNPUBLISH_REQUESTED, WorkflowStatus.PUBLISHED);
        verify(eventUnpublishRequestRepo).reviewIfCurrent(
                77L, adminRef, UnpublishRequestStatus.PENDING, UnpublishRequestStatus.REJECTED, "缺少營業執照");
        assertThat(result.eventName()).isEqualTo("夏日市集");
        assertThat(result.newEventStatus()).isEqualTo(EventStatus.PUBLISHED);

        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepo).save(notificationCaptor.capture());
        Notification savedNotification = notificationCaptor.getValue();
        assertThat(savedNotification.getUser()).isSameAs(organizerRef);
        assertThat(savedNotification.getCategory()).isEqualTo(NotificationCategory.EVENT_CHANGE);
        assertThat(savedNotification.getType()).isEqualTo(NotificationType.EVENT_UNPUBLISH_REQUEST_REVISION_REQUIRED);
        assertThat(savedNotification.getTargetType()).isEqualTo(NotificationTargetType.EVENT_UNPUBLISH_REQUEST);
        assertThat(savedNotification.getTargetId()).isEqualTo(77L);
        assertThat(savedNotification.getTitle()).isEqualTo("補件通知");
        assertThat(savedNotification.getContent())
                .isEqualTo("夏日市集的下架申請需要補件，請修改後重新送出審核，若有問題請洽公司聯絡電話");

        ArgumentCaptor<AdminOperationLog> logCaptor = ArgumentCaptor.forClass(AdminOperationLog.class);
        verify(logRepo).save(logCaptor.capture());
        AdminOperationLog savedLog = logCaptor.getValue();
        assertThat(savedLog.getUser()).isSameAs(adminRef);
        assertThat(savedLog.getOperationType()).isEqualTo(AdminOperationType.REQUEST_REVISION);
        assertThat(savedLog.getTargetType()).isEqualTo(AdminTargetType.EVENT_UNPUBLISH_REQUEST);
        assertThat(savedLog.getTargetId()).isEqualTo(77L);
        assertThat(savedLog.getTargetLabel()).isEqualTo("夏日市集");
        assertThat(savedLog.getContent()).isEqualTo("管理員小明退回夏日市集下架申請, 原因:缺少營業執照");
    }

    @Test void setEventUnpublishRequestRejectRestoresToFinalReviewWhenBrandAlreadyPublic() {
        when(userRepo.findAdminLookupByEmailAndRole("op@test.com", Role.ADMIN))
                .thenReturn(Optional.of(new AdminLookupProjection(9L, "管理員小明")));
        when(eventUnpublishRequestRepo.findReviewInfoById(77L)).thenReturn(Optional.of(
                new EventUnpublishReviewProjection(
                        UnpublishRequestStatus.PENDING, 1L, WorkflowStatus.UNPUBLISH_REQUESTED, "夏日市集",
                        LocalDateTime.of(2026, 1, 1, 0, 0), 5L)));
        when(userRepo.getReferenceById(9L)).thenReturn(new User());
        when(userRepo.getReferenceById(5L)).thenReturn(new User());

        var result = service.setEventUnpublishRequestReject(77L, "op@test.com", Role.ADMIN, "缺少營業執照");

        verify(eventRepo).updateWorkflowStatusIfCurrent(1L, WorkflowStatus.UNPUBLISH_REQUESTED, WorkflowStatus.FINAL_REVIEW);
        assertThat(result.eventName()).isEqualTo("夏日市集");
        assertThat(result.newEventStatus()).isEqualTo(EventStatus.PUBLISHED);
    }

}
