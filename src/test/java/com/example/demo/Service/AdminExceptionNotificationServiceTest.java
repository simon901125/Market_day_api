package com.example.demo.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.demo.Repository.NotificationRepo;
import com.example.demo.entity.Notification;
import com.example.demo.entity.User;
import com.example.demo.enums.notification.NotificationCategory;
import com.example.demo.enums.notification.NotificationTargetType;
import com.example.demo.enums.notification.NotificationType;

@ExtendWith(MockitoExtension.class)
class AdminExceptionNotificationServiceTest {

    @Mock NotificationRepo notificationRepo;
    @InjectMocks AdminExceptionNotificationService service;

    @Test
    void storesMissingUnpublishRequestWarningWithoutDedupKey() {
        User admin = new User();
        admin.setId(9L);

        service.notifyMissingUnpublishRequest(admin, 1L, "夏日市集");

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepo).save(captor.capture());
        Notification notification = captor.getValue();
        assertThat(notification.getUser()).isSameAs(admin);
        assertThat(notification.getCategory()).isEqualTo(NotificationCategory.EXCEPTION);
        assertThat(notification.getType()).isEqualTo(NotificationType.SYSTEM_EXCEPTION);
        assertThat(notification.getTargetType()).isEqualTo(NotificationTargetType.MARKET_EVENT);
        assertThat(notification.getTargetId()).isEqualTo(1L);
        assertThat(notification.getDedupKey()).isNull();
    }
}
