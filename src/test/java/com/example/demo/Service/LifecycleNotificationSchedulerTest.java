package com.example.demo.Service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.demo.Repository.LifecycleNotificationRepository;
import com.example.demo.Repository.LifecycleNotificationRepository.EndedEventRecipient;
import com.example.demo.Repository.LifecycleNotificationRepository.ExpiredApplication;

@ExtendWith(MockitoExtension.class)
class LifecycleNotificationSchedulerTest {

    @Mock LifecycleNotificationRepository lifecycleRepository;
    @Mock NotificationService notificationService;
    @InjectMocks LifecycleNotificationScheduler scheduler;

    @Test
    void expiresPendingApplicationAndNotifiesVendor() {
        when(lifecycleRepository.expirePendingApplications(any()))
                .thenReturn(List.of(new ExpiredApplication(11L, 21L, "逾期測試活動")));

        scheduler.expireOverduePayments();

        verify(lifecycleRepository).expirePendingPaymentRecords(List.of(11L));
        verify(notificationService).notifyPaymentExpired(21L, 11L, "逾期測試活動");
    }

    @Test
    void notifiesEligibleVendorWhenPublishedEventHasEnded() {
        when(lifecycleRepository.findEndedEventRecipients(any()))
                .thenReturn(List.of(new EndedEventRecipient(31L, 41L, "結束測試活動")));

        scheduler.notifyEndedEvents();

        verify(notificationService).notifyEventEnded(41L, 31L, "結束測試活動");
    }
}
