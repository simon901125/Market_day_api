package com.example.demo.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.demo.Repository.AdminLogRepo;
import com.example.demo.Repository.EventRepo;
import com.example.demo.Repository.EventStallZoneRepo;
import com.example.demo.Repository.UserRepo;
import com.example.demo.enums.status.UserStatus;
import com.example.demo.enums.status.WorkflowStatus;
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

    @Test void unfinishedServiceMethodsFailExplicitlyInsteadOfReturningMisleadingData() {
        assertThatThrownBy(() -> service.getNotice(null, 1, 10)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> service.getVenderDetail(1L)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> service.getVenderRegLogs(1L, 1, 10)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> service.getOrganizerDetail(1L)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> service.getOrgEventLogs(1L, 1, 10)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> service.getUserLoginLogs(1L, 1, 10)).isInstanceOf(UnsupportedOperationException.class);
    }
}
