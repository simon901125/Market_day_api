package com.example.demo.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.demo.Repository.AutomaticStallAssignmentRepository;
import com.example.demo.Repository.AutomaticStallAssignmentRepository.AssignmentEvent;
import com.example.demo.Repository.AutomaticStallAssignmentRepository.CompletedApplication;
import com.example.demo.Repository.AutomaticStallAssignmentRepository.PendingDate;

@ExtendWith(MockitoExtension.class)
class AutomaticStallAssignmentServiceTest {

    @Mock AutomaticStallAssignmentRepository repository;
    @Mock NotificationService notificationService;
    @InjectMocks AutomaticStallAssignmentService service;

    @Test
    void assignsEachApplicationDateThenMovesEventToFinalReview() {
        LocalDateTime now = LocalDateTime.now();
        LocalDate firstDay = LocalDate.now().plusDays(10);
        LocalDate secondDay = firstDay.plusDays(1);
        when(repository.lockDueEvent(1L, now))
                .thenReturn(new AssignmentEvent(1L, "夏日市集", 9L));
        when(repository.findPendingDates(1L)).thenReturn(List.of(
                new PendingDate(101L, 11L, firstDay),
                new PendingDate(102L, 11L, secondDay)));
        when(repository.findFirstAvailableStall(1L, firstDay)).thenReturn(201L);
        when(repository.findFirstAvailableStall(1L, secondDay)).thenReturn(202L);
        when(repository.assignStall(101L, firstDay, 201L)).thenReturn(1);
        when(repository.assignStall(102L, secondDay, 202L)).thenReturn(1);
        when(repository.findCompletedApplications(1L, List.of(11L))).thenReturn(List.of(
                new CompletedApplication(11L, 8L, "夏日市集", "森林手作")));
        when(repository.countIncompleteEligibleApplications(1L)).thenReturn(0);
        when(repository.finishFinalReview(1L, now)).thenReturn(1);

        assertThat(service.assignEvent(1L, now)).isTrue();

        verify(repository).findFirstAvailableStall(1L, firstDay);
        verify(repository).assignStall(101L, firstDay, 201L);
        verify(repository).findFirstAvailableStall(1L, secondDay);
        verify(repository).assignStall(102L, secondDay, 202L);
        verify(notificationService).notifyStallSelectionCompleted(8L, 11L, "夏日市集");
        verify(notificationService).notifyOrganizerStallSelectionCompleted(
                9L, 11L, "夏日市集", "森林手作");
        verify(repository).cancelUnpaidApplications(1L);
        verify(repository).finishFinalReview(1L, now);
    }

    @Test
    void keepsPublishedWhenAnyEligibleApplicationStillHasNoStall() {
        LocalDateTime now = LocalDateTime.now();
        LocalDate applyDate = LocalDate.now().plusDays(10);
        when(repository.lockDueEvent(1L, now))
                .thenReturn(new AssignmentEvent(1L, "夏日市集", 9L));
        when(repository.findPendingDates(1L))
                .thenReturn(List.of(new PendingDate(101L, 11L, applyDate)));
        when(repository.findFirstAvailableStall(1L, applyDate)).thenReturn(null);
        when(repository.findCompletedApplications(1L, List.of())).thenReturn(List.of());

        assertThat(service.assignEvent(1L, now)).isFalse();

        verify(repository, never()).finishFinalReview(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(notificationService, never()).notifyStallSelectionCompleted(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void skipsEventThatIsNoLongerDueAndPublished() {
        LocalDateTime now = LocalDateTime.now();
        when(repository.lockDueEvent(1L, now)).thenReturn(null);

        assertThat(service.assignEvent(1L, now)).isFalse();

        verify(repository, never()).findPendingDates(1L);
        verify(repository, never()).cancelUnpaidApplications(1L);
    }

    @Test
    void propagatesNotificationFailureSoAssignmentTransactionCanRollBack() {
        LocalDateTime now = LocalDateTime.now();
        LocalDate applyDate = LocalDate.now().plusDays(10);
        when(repository.lockDueEvent(1L, now))
                .thenReturn(new AssignmentEvent(1L, "通知回滾測試市集", 9L));
        when(repository.findPendingDates(1L))
                .thenReturn(List.of(new PendingDate(101L, 11L, applyDate)));
        when(repository.findFirstAvailableStall(1L, applyDate)).thenReturn(201L);
        when(repository.assignStall(101L, applyDate, 201L)).thenReturn(1);
        when(repository.findCompletedApplications(1L, List.of(11L))).thenReturn(List.of(
                new CompletedApplication(11L, 8L, "通知回滾測試市集", "回滾測試品牌")));
        doThrow(new IllegalStateException("notification unavailable"))
                .when(notificationService)
                .notifyStallSelectionCompleted(8L, 11L, "通知回滾測試市集");

        assertThatThrownBy(() -> service.assignEvent(1L, now))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("notification unavailable");

        verify(repository, never()).finishFinalReview(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(notificationService, never()).notifyOrganizerStallSelectionCompleted(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }
}
