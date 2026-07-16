package com.example.demo.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
import com.example.demo.Repository.UserRepo;
import com.example.demo.Repository.projection.admin.AdminLookupProjection;
import com.example.demo.Repository.projection.admin.UserAccountStatusProjection;
import com.example.demo.entity.AdminOperationLog;
import com.example.demo.entity.User;
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
    AdminService service;

    @BeforeEach void setUp() {
        service = new AdminService();
        ReflectionTestUtils.setField(service, "eventRepo", eventRepo);
        ReflectionTestUtils.setField(service, "eventStallZoneRepo", zoneRepo);
        ReflectionTestUtils.setField(service, "userRepo", userRepo);
        ReflectionTestUtils.setField(service, "logRepo", logRepo);
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
                .hasMessage("找不到該管理員");
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
                .hasMessage("找不到該管理員");
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

}
