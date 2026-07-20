package com.example.demo.Service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.Repository.LifecycleNotificationRepository;
import com.example.demo.Repository.LifecycleNotificationRepository.EndedEventRecipient;
import com.example.demo.Repository.LifecycleNotificationRepository.ExpiredApplication;

@Service
public class LifecycleNotificationScheduler {

    private static final long TEN_MINUTES = 600_000L;

    private final LifecycleNotificationRepository lifecycleRepository;
    private final NotificationService notificationService;

    public LifecycleNotificationScheduler(
            LifecycleNotificationRepository lifecycleRepository,
            NotificationService notificationService) {
        this.lifecycleRepository = lifecycleRepository;
        this.notificationService = notificationService;
    }

    @Scheduled(fixedDelay = TEN_MINUTES, initialDelay = TEN_MINUTES)
    @Transactional
    public void expireOverduePayments() {
        List<ExpiredApplication> expired = lifecycleRepository.expirePendingApplications(LocalDateTime.now());
        lifecycleRepository.expirePendingPaymentRecords(
                expired.stream().map(ExpiredApplication::applicationId).toList());
        expired.forEach(application -> notificationService.notifyPaymentExpired(
                application.vendorUserId(),
                application.applicationId(),
                application.eventTitle()));
    }

    @Scheduled(fixedDelay = TEN_MINUTES, initialDelay = TEN_MINUTES)
    @Transactional
    public void notifyEndedEvents() {
        List<EndedEventRecipient> recipients =
                lifecycleRepository.findEndedEventRecipients(LocalDateTime.now());
        recipients.forEach(recipient -> notificationService.notifyEventEnded(
                recipient.vendorUserId(),
                recipient.eventId(),
                recipient.eventTitle()));
    }
}
